# Migration Guide — Multi-module rearchitecture (1.2.0)

This guide covers upgrading from the pre-1.2 single-module `:shared` API
(`CacheConfig` / `CacheStorageConfig` / `installPersistentCache`) to the multi-module
rearchitecture: `:cache-core` (the algorithm and DSL, zero I/O dependencies),
`:cache-okio` (the default, stable backend), and `:cache-kotlinx-io` (an experimental
opt-in backend). `:shared` keeps its Maven coordinate
(`io.github.santimattius:ktor-persistent-cache`) and now delegates to `cache-core` +
`cache-okio` as a deprecated compatibility facade.

---

## 1. Breaking change: `CacheDirectoryProvider` has no migration path

Read this section first if you implement a custom `CacheDirectoryProvider`. Everything
else in this guide is a deprecation with an IDE quick-fix; this one is not.

**Before** (pre-1.2, `:shared`):

```kotlin
import io.github.santimattius.persistent.cache.CacheDirectoryProvider
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class MyCacheDirectoryProvider : CacheDirectoryProvider {
    override val cacheDirectory: Path
        get() = FileSystem.SYSTEM.toPath("/custom/cache/dir")
}
```

**After** (1.2+, `:cache-core`):

```kotlin
import io.github.santimattius.persistent.cache.CacheDirectoryProvider

class MyCacheDirectoryProvider : CacheDirectoryProvider {
    override val cacheDirectory: String
        get() = "/custom/cache/dir"
}
```

`CacheDirectoryProvider` keeps the exact same fully-qualified name,
`io.github.santimattius.persistent.cache.CacheDirectoryProvider`, but the
`cacheDirectory` property changed type from `okio.Path` to `String` so that
`cache-core` has zero Okio/kotlinx-io dependencies (a stated goal of this
rearchitecture — see the proposal). Kotlin cannot host two declarations with the same
FQN and different signatures across modules, so `:shared` cannot keep a deprecated
`okio.Path`-returning overload of the same interface alongside `cache-core`'s
`String`-returning one. The old `:shared` copy was deleted outright, with **no**
`@Deprecated`/`ReplaceWith` compatibility shim — this is an intentional, accepted
break, not an oversight.

**You must rewrite any custom `CacheDirectoryProvider` implementation manually.** There
is no automatic migration: change the return type annotation from `Path` to `String`,
and return a plain path string instead of an `okio.Path` (typically by dropping the
`FileSystem.SYSTEM.toPath(...)` wrapper, or calling `.toString()` on an existing
`Path` if you still build one internally for other reasons).

---

## 2. New DSL: `install(PersistentCache) { ... }`

`installPersistentCache(CacheConfig(...))` is deprecated in favor of
`install(PersistentCache) { ... }`, built on Ktor's `createClientPlugin`. One
consolidated `PersistentCacheConfig` type replaces `CacheConfig` /
`CacheStorageConfig`, with no field renaming across layers.

**Before:**

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.github.santimattius.persistent.cache.*

val client = HttpClient(CIO) {
    installPersistentCache(
        CacheConfig(
            enabled = true,
            cacheDirectory = "http_cache",
            maxCacheSize = 10L * 1024 * 1024,
            cacheTtl = 60 * 60 * 1000,
            isShared = true,
            isPublic = false
        )
    )
}
```

**After:**

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.github.santimattius.persistent.cache.*

@OptIn(InternalPersistentCacheApi::class)
fun createClient(): HttpClient = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        maxSize = 10L * 1024 * 1024
        ttl = 60 * 60 * 1000
        shared = true
        public = false
        fileSystem = OkioCacheFileSystem() // from :cache-okio — see section 3
    }
}
```

Two things are different from the old API, both intentional:

- **`fileSystem` is required.** `cache-core` ships no default backend — it has zero
  I/O dependencies by design — so you must assign a `CacheFileSystem` explicitly. Use
  `OkioCacheFileSystem()` (the default, stable choice, from `:cache-okio`) or
  `KotlinxIoCacheFileSystem()` (experimental, from `:cache-kotlinx-io`; see section 3).
- **`fileSystem` and `directoryProvider` require an opt-in.** They are typed against
  the backend SPI (`CacheFileSystem<*>` / the SPI-facing constructors), annotated
  `@InternalPersistentCacheApi`. This is a real, intentional design choice (not a bug):
  the SPI is public because Kotlin has no cross-module `internal`, but it is not a
  stable, supported API for application code the way `PersistentCacheConfig`'s other
  properties are. Add `@OptIn(InternalPersistentCacheApi::class)` to the function or
  class that calls `install(PersistentCache)`.

### Field mapping

| Old (`CacheConfig`) | New (`PersistentCacheConfig`) |
|---|---|
| `enabled` | No field equivalent — omit `install(PersistentCache)` entirely to disable caching |
| `cacheDirectory` | `directory` |
| `maxCacheSize` | `maxSize` |
| `cacheTtl` | `ttl` |
| `isShared` | `shared` |
| `isPublic` | `public` |
| *(none — always Okio)* | `fileSystem` (required; choose a backend, see section 3) |
| `cacheDirectoryProvider` parameter | `directoryProvider` |

---

## 3. Choosing a backend

### `cache-okio` — default, stable

Ships transitively via `:shared`, so if you already depend on
`io.github.santimattius:ktor-persistent-cache`, no extra dependency is needed:

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache:1.2.0")
        // pulls in :cache-core and :cache-okio transitively
    }
}
```

Or depend on `cache-core` + `cache-okio` directly, without the `:shared` facade:

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache-core:1.2.0")
        implementation("io.github.santimattius:ktor-persistent-cache-okio:1.2.0")
    }
}
```

```kotlin
@OptIn(InternalPersistentCacheApi::class)
fun createClient(): HttpClient = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        fileSystem = OkioCacheFileSystem() // defaults to okio.FileSystem.SYSTEM
    }
}
```

### `cache-kotlinx-io` — experimental, opt-in

Not pulled in by `:shared`. Add it directly alongside `cache-core`:

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache-core:1.2.0")
        implementation("io.github.santimattius:ktor-persistent-cache-kotlinx-io:1.2.0")
    }
}
```

```kotlin
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
fun createClient(): HttpClient = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        fileSystem = KotlinxIoCacheFileSystem() // defaults to kotlinx.io.files.SystemFileSystem
    }
}
```

`KotlinxIoCacheFileSystem` requires **two** opt-ins: `InternalPersistentCacheApi` (the
backend SPI, same as `cache-okio`) and `ExperimentalKotlinxIoCache` (`WARNING`-level —
this backend tracks kotlinx-io's own Alpha-stability `kotlinx.io.files` package and may
change shape between minor versions of this library). `cache-kotlinx-io` is kept on the
same release version train as every other module rather than versioned separately.
**Okio remains the recommended default backend** for production use; choose
`cache-kotlinx-io` only if you already depend on kotlinx-io and want to avoid pulling
in Okio.

Both backends pass the same shared conformance test suite and produce byte-identical
on-disk cache filenames for the same input (SHA-256-based keys), so switching backends
does not invalidate an existing on-disk cache.

---

## 4. Deprecated symbols reference

| Old symbol (`:shared`, deprecated) | New equivalent |
|---|---|
| `CacheConfig` | `PersistentCacheConfig`, configured inside `install(PersistentCache) { ... }` (`:cache-core`) |
| `CacheStorageConfig` | `PersistentCacheConfig` (no automatic quick-fix — interface/supertype usage, see section 2's field mapping) |
| `CacheStorageFactory.create(...)` | `install(PersistentCache) { ... }` for the common case; `FileCacheStorage(...)` directly (`:cache-core`, requires `@OptIn(InternalPersistentCacheApi::class)`) if you specifically need a raw `CacheStorage` |
| `installPersistentCache(CacheConfig(...))` | `install(PersistentCache) { ... }` (`:cache-core`) |
| `CacheDirectoryProvider` (returning `okio.Path`) | `CacheDirectoryProvider` (same FQN, now returning `String`, in `:cache-core`) — **no `ReplaceWith`, manual rewrite required, see section 1** |

All deprecated `:shared` symbols above (except `CacheDirectoryProvider`) carry
`@Deprecated(ReplaceWith(...))` and remain source- and binary-compatible: existing code
using them continues to compile and behave identically, emitting a deprecation
warning. They are gated by a committed binary-compatibility-validator (`apiCheck`)
baseline, so any accidental removal or signature change fails CI.

---

## Background: why `cache-core` requires opting into an internal SPI

`PersistentCacheConfig.fileSystem` and `.directoryProvider` are typed against
`CacheFileSystem<*>` / `CacheDirectoryProvider`, both reachable only through backend
types annotated `@InternalPersistentCacheApi`. This SPI is deliberately kept **in**
each module's committed `apiCheck` baseline (not added to `nonPublicMarkers`), so any
unintentional drift in its shape is caught by CI rather than silently shipped — the
opt-in annotation communicates "not a stability-guaranteed API for application code"
without hiding it from API review. `klib` (iOS) ABI validation is additionally enabled
on `cache-core` only, as of this release, proven stable on the CI runner; the other
three publishable modules currently validate JVM API only.
