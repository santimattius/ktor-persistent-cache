# Ktor Persistent Cache

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Ktor](https://img.shields.io/badge/Ktor-3.4.0-000000?logo=ktor&logoColor=white)](https://ktor.io)
[![Android](https://img.shields.io/badge/Android-AGP%209.0-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-Supported-8E8E93?logo=apple&logoColor=white)](https://developer.apple.com)
[![JVM](https://img.shields.io/badge/JVM-Supported-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

A **Kotlin Multiplatform** library that adds **persistent HTTP caching** to [Ktor](https://ktor.io)
HttpClient via an idiomatic client plugin DSL, with a storage backend you choose explicitly —
[Okio](https://github.com/square/okio) (default, stable) or
[kotlinx-io](https://github.com/Kotlin/kotlinx-io) (experimental) — configurable size limits, TTL,
and platform-appropriate cache directories.

> **Upgrading from a pre-1.2 version?** The public API moved to a multi-module layout and a new
> `install(PersistentCache) { ... }` DSL. See [docs/MIGRATION.md](docs/MIGRATION.md) — it leads
> with the one unavoidable breaking change (a custom `CacheDirectoryProvider` needs a manual
> rewrite) before covering everything else, which is a source- and binary-compatible deprecation.

---

## Features

- **Persistent storage** — Cache survives app restarts; stored on the filesystem.
- **Kotlin Multiplatform** — Shared API for **Android**, **iOS**, and **JVM**.
- **Ktor integration** — An idiomatic `install(PersistentCache) { ... }` client plugin built on
  Ktor's own [HttpCache](https://ktor.io/docs/client-caching.html); you configure storage and
  options in one place.
- **Configurable** — TTL (time-to-live), max cache size, directory name, shared vs unshared, and
  public vs private storage.
- **Choice of storage backend** — [`cache-okio`](#choosing-a-backend) (default, stable) or
  [`cache-kotlinx-io`](#choosing-a-backend) (experimental, opt-in), both implementing the same
  [`CacheFileSystem`][CacheFileSystem] SPI on top of one shared caching algorithm in
  [`cache-core`][cache-core], so switching backends does not invalidate an existing on-disk cache.
- **Content negotiation** — Respects `Vary` headers so different variants (e.g. by
  `Accept-Language`) are cached separately.
- **LRU eviction** — When the cache exceeds the configured size, least-recently-used entries are
  removed.
- **Custom cache location** — Optional [`CacheDirectoryProvider`][CacheDirectoryProvider] for
  custom cache root paths (e.g. for tests or special directories).
- **API stability gate** — Every publishable module is checked by
  [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator);
  unreviewed public API changes fail CI.

[CacheDirectoryProvider]: #custom-cache-directory
[CacheFileSystem]: cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheFileSystem.kt
[cache-core]: cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache

---

## Supported platforms

| Platform    | Cache directory                                         |
|-------------|---------------------------------------------------------|
| **Android** | Application cache dir (`context.cacheDir`)              |
| **iOS**     | App caches directory (NSCachesDirectory in the sandbox) |
| **JVM**     | `java.io.tmpdir/ktor-cache`                             |

---

## Requirements

- **Kotlin** 2.3.0+
- **Ktor** HttpClient (e.g. `ktor-client-core` 3.4.0+) and an engine (CIO, OkHttp, etc.) for your
  targets
- **Android**: minSdk 24+, JDK 11+
- **iOS**: Standard deployment targets
- **JVM**: JDK 11+

---

## Installation

This library ships as four Maven artifacts. Pick one of the two paths below.

**Path A — just depend on `ktor-persistent-cache`** (recommended for most users): it transitively
pulls in `cache-core` and the default `cache-okio` backend, so `install(PersistentCache) { ... }`
works with no extra setup.

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache:1.2.0")
    }
    // Also add a Ktor engine for each target, e.g.:
    // implementation("io.ktor:ktor-client-okhttp")   // Android
    // implementation("io.ktor:ktor-client-cio")     // iOS / JVM
}
```

**Path B — depend on `cache-core` plus a backend directly**, without the `:shared` facade — use
this if you want the experimental `cache-kotlinx-io` backend instead, or want the smallest
possible dependency surface:

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache-core:1.2.0")
        implementation("io.github.santimattius:ktor-persistent-cache-okio:1.2.0")
        // or, instead of the line above:
        // implementation("io.github.santimattius:ktor-persistent-cache-kotlinx-io:1.2.0")
    }
}
```

```toml
# gradle/libs.versions.toml
[versions]
ktorPersistentCache = "1.2.0"

[libraries]
ktor-persistent-cache = { group = "io.github.santimattius", name = "ktor-persistent-cache", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-core = { group = "io.github.santimattius", name = "ktor-persistent-cache-core", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-okio = { group = "io.github.santimattius", name = "ktor-persistent-cache-okio", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-kotlinx-io = { group = "io.github.santimattius", name = "ktor-persistent-cache-kotlinx-io", version.ref = "ktorPersistentCache" }
```

```kotlin
repositories {
    mavenCentral()
    // For snapshots:
    // maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}
```

---

## Setup

### Android

The library needs the **application context** to resolve the cache directory. The recommended way is
**App Startup**:

1. **Merge the library's manifest**
   The `shared` (or Android) module that depends on `ktor-persistent-cache` should merge the
   library's AndroidManifest so that the App Startup `InitializationProvider` and
   `ContextInitializer` (now in `cache-core`) are registered.

2. **No extra code**
   If the manifest is merged, `ContextInitializer` runs at app startup and injects the application
   context. [getCacheDirectoryProvider()](cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheDirectoryProvider.kt)
   will then use it automatically.

If you **don't** use the library's manifest (e.g. you use a different DI or startup path), you must
call **once** at app startup with the **application context** (not an Activity context):

```kotlin
import io.github.santimattius.persistent.cache.startup.injectContext

// e.g. in Application.onCreate()
injectContext(applicationContext)
```

`injectContext` is a **public** API in `io.github.santimattius.persistent.cache.startup`. It throws
`IllegalArgumentException` if you pass a context that can leak memory (for example an Activity).

### iOS

No setup. The library uses the default app caches directory.

### JVM

No setup. The library uses a subdirectory of the JVM temp directory.

---

## Quick start

1. Create an [HttpClient](https://ktor.io/docs/create-client.html) and call **`install(PersistentCache)`**:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.github.santimattius.persistent.cache.*

@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        maxSize = 10L * 1024 * 1024 // 10 MB
        ttl = 60 * 60 * 1000        // 1 hour
        shared = true
        public = false
        fileSystem = OkioCacheFileSystem() // the default, stable backend — see "Choosing a backend"
    }
}
```

`fileSystem` is required: `cache-core` ships zero I/O dependencies by design, so you choose a
backend explicitly (see [Choosing a backend](#choosing-a-backend) below). `fileSystem` and
`directoryProvider` are typed against a backend SPI annotated `@InternalPersistentCacheApi` — not a
bug, an intentional signal that the SPI itself isn't a stability-guaranteed surface for application
code the way the rest of `PersistentCacheConfig` is. Add
`@OptIn(InternalPersistentCacheApi::class)` where you call `install(PersistentCache)`.

2. Use the client as usual. The cache stores responses for requests that support caching and serves
   them when valid.

   **Cross-restart persistence** depends on the **origin server's** cacheability headers per
   [RFC 7234](https://www.rfc-editor.org/rfc/rfc7234) (for example `Cache-Control: max-age=…` or
   `Expires`). This library persists whatever Ktor's [HttpCache](https://ktor.io/docs/client-caching.html)
   plugin stores; it does not override freshness rules. Responses marked `no-store` are not written
   to disk.

```kotlin
val response: String = client.get("https://example.com/api/data").body()
```

3. Optionally pass a custom [`CacheDirectoryProvider`][CacheDirectoryProvider] via `directoryProvider`
   (see [Custom cache directory](#custom-cache-directory)).

---

## Configuration

`PersistentCacheConfig` (configured inside `install(PersistentCache) { ... }`) supports:

| Property           | Type                  | Default        | Description                                                                                                                 |
|---------------------|-----------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------|
| `directory`         | `String`              | `"http_cache"` | Name of the cache directory under the platform cache root.                                                                  |
| `maxSize`           | `Long`                | 10 MB          | Maximum cache size in bytes. LRU eviction when exceeded. Use `0` for no limit.                                              |
| `ttl`               | `Long`                | 1 hour         | Time-to-live for entries in milliseconds. Values `<= 0` mean entries **never expire** (same convention as `maxSize <= 0` = unlimited). |
| `shared`            | `Boolean`              | `true`         | Whether the cache is shared across requests (Ktor [HttpCache](https://ktor.io/docs/client-caching.html) behavior).          |
| `public`            | `Boolean`              | `false`        | When `true`, cached responses are treated as public (shareable across users); when `false`, they are private to the client. |
| `fileSystem`        | `CacheFileSystem<*>?`  | `null`         | **Required.** The storage backend — e.g. `OkioCacheFileSystem()` or `KotlinxIoCacheFileSystem()`. `@InternalPersistentCacheApi`. |
| `directoryProvider`  | `CacheDirectoryProvider?` | `null`      | Optional custom cache root; defaults to the platform-specific [`getCacheDirectoryProvider()`][CacheDirectoryProvider]. `@InternalPersistentCacheApi`. |
| `clock`             | `() -> Long`           | `getTimeMillis()` | Supplies the current time; override for deterministic tests.                                                            |

There is no direct `enabled` toggle: to disable caching, omit `install(PersistentCache)` entirely.

### Auth plugin + persistent cache

This library installs Ktor's [HttpCache](https://ktor.io/docs/client-caching.html) and does **not**
intercept the Auth pipeline. When you also install [Auth](https://ktor.io/docs/client-auth.html),
behavior follows Ktor's cache routing:

- Authenticated responses need **`Cache-Control: private`** (for example `private, max-age=3600`) to
  be stored in **private** storage — the backend-persisted store configured by
  `install(PersistentCache)` when `public = false`. Responses with only `max-age` (no `private`)
  route to Ktor's default public storage, which this plugin does not configure.
- Set **`shared = false`** on `PersistentCacheConfig` when using Bearer (or other) auth on the same
  client. Ktor skips cache lookup for authorized requests on a shared client and refuses to store
  private entries when `shared = true`.

These rules are verified by `AuthPluginInteropTest` (in `cache-okio`'s test suite, exercised against
the real `install(PersistentCache)` DSL). No extra configuration is required beyond matching server
cache headers and the `PersistentCacheConfig` flags above.

---

## Architecture: `cache-core` and storage backends

The library is split across four published modules:

| Module | Artifact | Contains |
|---|---|---|
| [`cache-core`][cache-core] | `ktor-persistent-cache-core` | The `install(PersistentCache) { ... }` DSL, the shared caching algorithm (`FileCacheStorage`), the [`CacheFileSystem`][CacheFileSystem] backend SPI, and `CacheDirectoryProvider`. Zero I/O dependencies — no Okio, no kotlinx-io. |
| `cache-okio` | `ktor-persistent-cache-okio` | `OkioCacheFileSystem` — the default, stable backend, implementing `CacheFileSystem<okio.Path>`. |
| `cache-kotlinx-io` | `ktor-persistent-cache-kotlinx-io` | `KotlinxIoCacheFileSystem` — an **experimental**, opt-in backend, implementing `CacheFileSystem<kotlinx.io.files.Path>`. |
| `ktor-persistent-cache` (the `:shared` module) | `ktor-persistent-cache` | A compatibility facade depending on `cache-core` + `cache-okio`, keeping the pre-1.2 Maven coordinate working. Also where `ContextInitializer`'s Android manifest merge lives (via `cache-core`). |

Both backends implement the same `CacheFileSystem<P>` contract, pass the same shared conformance
test suite, and produce byte-identical on-disk cache filenames for the same input (SHA-256-based
keys) — switching backends does not invalidate an existing on-disk cache.

### Choosing a backend

**`cache-okio`** — default, stable, ships transitively via `ktor-persistent-cache`:

```kotlin
@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        fileSystem = OkioCacheFileSystem() // defaults to okio.FileSystem.SYSTEM
    }
}
```

**`cache-kotlinx-io`** — experimental, opt-in, **not** pulled in by `ktor-persistent-cache`; add the
`ktor-persistent-cache-kotlinx-io` artifact directly (see [Installation, Path B](#installation)):

```kotlin
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        fileSystem = KotlinxIoCacheFileSystem() // defaults to kotlinx.io.files.SystemFileSystem
    }
}
```

`KotlinxIoCacheFileSystem` requires the extra `ExperimentalKotlinxIoCache` opt-in (`WARNING`-level)
because it tracks kotlinx-io's own Alpha-stability `kotlinx.io.files` package and may change shape
between minor versions of this library. **Okio remains the recommended default backend** for
production use; choose `cache-kotlinx-io` only if you already depend on kotlinx-io and want to avoid
pulling in Okio.

See [docs/MIGRATION.md](docs/MIGRATION.md) if you're upgrading from a version that used
`CacheStorageFactory`/`CacheConfig` directly.

---

## Custom cache directory

To control where the cache is stored (e.g. a custom folder or test directory), implement
[`CacheDirectoryProvider`][CacheDirectoryProvider] and assign it to `directoryProvider`:

```kotlin
val customProvider = object : CacheDirectoryProvider {
    override val cacheDirectory: String get() = "/custom/cache/dir"
}

@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        fileSystem = OkioCacheFileSystem()
        directoryProvider = customProvider
    }
}
```

Default behavior (no custom provider): [`getCacheDirectoryProvider()`][getCacheDirectoryProvider]
returns the platform implementation (Android app cache dir, iOS caches dir, or JVM temp dir).
Note `cacheDirectory` here is a plain `String`, not an `okio.Path` — see
[docs/MIGRATION.md](docs/MIGRATION.md) if you're upgrading a pre-1.2 custom provider.

[getCacheDirectoryProvider]: cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheDirectoryProvider.kt

---

## Building and testing (from source)

**Build:**

```bash
./gradlew build
```

**Run tests and API checks (all modules):**

```bash
./gradlew check apiCheck
```

**Publish to local Maven:**

```bash
./gradlew publishToMavenLocal
```

Then depend on `io.github.santimattius:ktor-persistent-cache:1.2.0` (or one of the other three
artifacts) with `mavenLocal()` in your project.

---

## Publishing

Each publishable module (`shared`, `cache-core`, `cache-okio`, `cache-kotlinx-io`) is published with
the [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin), and
gated by a committed [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
baseline (`apiCheck`) — unreviewed public API changes fail CI.

| Action                   | Command / Doc                                                           |
|--------------------------|-------------------------------------------------------------------------|
| Publish to local Maven   | `./gradlew publishToMavenLocal`                                         |
| Publish to Maven Central | See [docs/PUBLISHING.md](docs/PUBLISHING.md) for credentials and steps. |

Coordinates and POM are configured in each module's `build.gradle.kts`.

---

## License

This project is licensed under
the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

---

## References

| Resource                | URL                                                                                 |
|--------------------------|-------------------------------------------------------------------------------------|
| Migration guide (1.2.0)  | [docs/MIGRATION.md](docs/MIGRATION.md)                                              |
| Ktor — HTTP client       | [ktor.io/docs/client](https://ktor.io/docs/client.html)                             |
| Ktor — Caching           | [ktor.io/docs/client-caching](https://ktor.io/docs/client-caching.html)             |
| Okio                     | [github.com/square/okio](https://github.com/square/okio)                           |
| kotlinx-io               | [github.com/Kotlin/kotlinx-io](https://github.com/Kotlin/kotlinx-io)                |
| Kotlin Multiplatform     | [kotlinlang.org/docs/multiplatform](https://kotlinlang.org/docs/multiplatform.html) |
| Publishing (this repo)   | [docs/PUBLISHING.md](docs/PUBLISHING.md)                                            |
