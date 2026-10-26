# Ktor Persistent Cache

[![Maven Central](https://img.shields.io/maven-central/v/io.github.santimattius/ktor-persistent-cache?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.santimattius/ktor-persistent-cache)
[![CI](https://github.com/santimattius/ktor-persistent-cache/actions/workflows/check.yml/badge.svg)](https://github.com/santimattius/ktor-persistent-cache/actions/workflows/check.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.6.0-000000?logo=ktor&logoColor=white)](https://ktor.io)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20JVM-lightgrey)

**Persistent HTTP caching for [Ktor](https://ktor.io) `HttpClient` on Kotlin Multiplatform.**
Responses cached by Ktor's [`HttpCache`](https://ktor.io/docs/client-caching.html) are stored on
disk, so they survive app restarts. You choose the storage backend —
[Okio](https://github.com/square/okio) (stable, default) or
[kotlinx-io](https://github.com/Kotlin/kotlinx-io) (experimental).

```kotlin
@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        fileSystem = OkioCacheFileSystem()
    }
}
```

> **Status:** `2.0.0-alpha02` is a pre-release. The API may still change before `2.0.0`; see the
> [CHANGELOG](CHANGELOG.md). Upgrading from a pre-1.2 version? Read the
> [migration guide](docs/MIGRATION.md) first — one change (a custom `CacheDirectoryProvider`) needs a
> manual rewrite.

## Contents

- [Quick start](#quick-start)
- [Features](#features)
- [Supported platforms and requirements](#supported-platforms-and-requirements)
- [Installation](#installation)
- [Platform setup](#platform-setup)
- [Configuration](#configuration)
- [Guides](#guides) — [backends](#choosing-a-backend), [Ktor `HttpCache` factories](#using-the-storage-with-ktor-httpcache),
  [custom directory](#custom-cache-directory), [Auth](#auth-plugin--persistent-cache)
- [How caching works (FAQ)](#how-caching-works-faq)
- [Architecture](#architecture)
- [Documentation](#documentation)
- [Building from source](#building-from-source)
- [Contributing, security and support](#contributing-security-and-support)
- [License](#license)

---

## Quick start

**1. Add the dependency** (plus a [Ktor engine](https://ktor.io/docs/client-engines.html) per target):

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.santimattius:ktor-persistent-cache:2.0.0-alpha02")
        }
    }
}
```

**2. Install the plugin:**

```kotlin
import io.github.santimattius.persistent.cache.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*

@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        directory = "http_cache"
        maxSize = 10L * 1024 * 1024 // 10 MB
        ttl = 60L * 60 * 1000       // 1 hour
        fileSystem = OkioCacheFileSystem()
    }
}
```

**3. Use the client as usual:**

```kotlin
val body: String = client.get("https://example.com/api/data").body()
```

**4. On Android only**, make sure the library can resolve the application context — see
[Platform setup](#platform-setup).

Notes:

- `fileSystem` is **required**. `cache-core` ships no I/O dependencies by design, so the backend is
  explicit.
- `fileSystem` and `directoryProvider` are typed against a backend SPI annotated
  `@InternalPersistentCacheApi`. That is an intentional signal that the SPI is not a
  stability-guaranteed surface, hence the `@OptIn` where you call `install(PersistentCache)`.
  If you would rather avoid the opt-in, use the
  [`HttpCache` factories](#using-the-storage-with-ktor-httpcache).
- What gets cached, and for how long, is decided by the **origin server's** headers — see the
  [FAQ](#how-caching-works-faq).

---

## Features

| Feature | What you get |
|---|---|
| **Persistent storage** | Cached responses live on the filesystem and survive app restarts. |
| **Kotlin Multiplatform** | One API for **Android**, **iOS** and **JVM**. |
| **Idiomatic Ktor plugin** | `install(PersistentCache) { ... }`, built on Ktor's own `HttpCache`. |
| **Choice of backend** | [`cache-okio`](#choosing-a-backend) (stable) or [`cache-kotlinx-io`](#choosing-a-backend) (experimental). Both share one caching engine and produce identical on-disk files, so switching backends keeps the cache valid. |
| **Configurable** | TTL, max size, directory name, shared vs. unshared, public vs. private storage. |
| **`Vary`-aware** | Variants (for example by `Accept-Language`) are cached separately. |
| **LRU eviction** | Least-recently-used entries are removed once the size limit is exceeded. |
| **Crash-safe writes** | Entries are written atomically and carry a format version. See [Storage behavior](docs/STORAGE.md). |
| **Custom location** | Optional [`CacheDirectoryProvider`](#custom-cache-directory) for tests or special directories. |
| **API stability gate** | Every published module is checked by [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator); unreviewed public API changes fail CI. |

---

## Supported platforms and requirements

| Platform | Cache directory | Requirement |
|---|---|---|
| **Android** | Application cache dir (`context.cacheDir`) | minSdk 24+, JVM target 17 |
| **iOS** | App caches directory (`NSCachesDirectory`, inside the sandbox) | `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| **JVM** | `java.io.tmpdir/ktor-cache` | JDK 17+ |

Built and tested with **Kotlin 2.4.20** and **Ktor 3.6.0** (`ktor-client-core`). You also need a Ktor
engine for each target (CIO, OkHttp, Darwin, ...).

---

## Installation

The library ships as four Maven artifacts. Pick one path.

### Path A — one dependency (recommended)

`ktor-persistent-cache` pulls in `cache-core` and the default `cache-okio` backend, so
`install(PersistentCache)` works with no extra setup.

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache:2.0.0-alpha02")
    }
    // Also add a Ktor engine for each target, e.g.:
    // implementation("io.ktor:ktor-client-okhttp")   // Android
    // implementation("io.ktor:ktor-client-cio")      // JVM
    // implementation("io.ktor:ktor-client-darwin")   // iOS
}
```

### Path B — `cache-core` plus a backend

Use this for the experimental `cache-kotlinx-io` backend, or for the smallest dependency surface.

```kotlin
dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache-core:2.0.0-alpha02")
        implementation("io.github.santimattius:ktor-persistent-cache-okio:2.0.0-alpha02")
        // or, instead of the line above:
        // implementation("io.github.santimattius:ktor-persistent-cache-kotlinx-io:2.0.0-alpha02")
    }
}
```

<details>
<summary>Version catalog (<code>gradle/libs.versions.toml</code>)</summary>

```toml
[versions]
ktorPersistentCache = "2.0.0-alpha02"

[libraries]
ktor-persistent-cache = { group = "io.github.santimattius", name = "ktor-persistent-cache", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-core = { group = "io.github.santimattius", name = "ktor-persistent-cache-core", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-okio = { group = "io.github.santimattius", name = "ktor-persistent-cache-okio", version.ref = "ktorPersistentCache" }
ktor-persistent-cache-kotlinx-io = { group = "io.github.santimattius", name = "ktor-persistent-cache-kotlinx-io", version.ref = "ktorPersistentCache" }
```

</details>

Artifacts are published to Maven Central:

```kotlin
repositories {
    mavenCentral()
}
```

---

## Platform setup

| Platform | Setup |
|---|---|
| **Android** | Needs the application context — see below. |
| **iOS** | None. Uses the default app caches directory. |
| **JVM** | None. Uses a subdirectory of the JVM temp directory. |

### Android

The library needs the **application context** to resolve the cache directory. The recommended way
is **App Startup**:

1. **Merge the library's manifest.** The module that depends on `ktor-persistent-cache` must merge
   the library's `AndroidManifest`, so the App Startup `InitializationProvider` and
   `ContextInitializer` (in `cache-core`) are registered.
2. **No extra code.** `ContextInitializer` runs at app startup and injects the application context.
   [`getCacheDirectoryProvider()`](cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheDirectoryProvider.kt)
   then uses it automatically.

If you do **not** use the library's manifest (for example you use a different DI or startup path),
call this **once** at app startup with the **application context** (not an Activity context):

```kotlin
import io.github.santimattius.persistent.cache.startup.injectContext

// e.g. in Application.onCreate()
injectContext(applicationContext)
```

`injectContext` is public API in `io.github.santimattius.persistent.cache.startup`. It throws
`IllegalArgumentException` if given a context that can leak memory, such as an Activity.

---

## Configuration

Options of `PersistentCacheConfig`, set inside `install(PersistentCache) { ... }`:

| Property | Type | Default | Description |
|---|---|---|---|
| `fileSystem` | `CacheFileSystem<*>?` | `null` | **Required.** The storage backend, e.g. `OkioCacheFileSystem()` or `KotlinxIoCacheFileSystem()`. `@InternalPersistentCacheApi`. |
| `directory` | `String` | `"http_cache"` | Name of the cache directory under the platform cache root. |
| `maxSize` | `Long` | 10 MB | Maximum cache size in bytes. LRU eviction when exceeded. `<= 0` means no limit. |
| `ttl` | `Long` | 1 hour | Time-to-live for entries, in milliseconds. `<= 0` means entries **never expire**. |
| `shared` | `Boolean` | `true` | Whether the cache is shared across requests (Ktor [`HttpCache`](https://ktor.io/docs/client-caching.html) behavior). |
| `public` | `Boolean` | `false` | When `true`, responses are treated as public (shareable across users); when `false`, private to the client. |
| `directoryProvider` | `CacheDirectoryProvider?` | `null` | Optional custom cache root; defaults to the platform [`getCacheDirectoryProvider()`](#custom-cache-directory). `@InternalPersistentCacheApi`. |
| `clock` | `() -> Long` | `getTimeMillis()` | Supplies the current time; override for deterministic tests. |

There is no `enabled` toggle: to disable caching, omit `install(PersistentCache)`.

---

## Guides

### Choosing a backend

Both backends implement the same `CacheFileSystem<P>` contract, pass the same conformance test
suite, and write byte-identical file names (SHA-256-based keys).

| | `cache-okio` | `cache-kotlinx-io` |
|---|---|---|
| Stability | **Stable (default)** | Experimental |
| Pulled in by `ktor-persistent-cache` | Yes | No — add the artifact ([Path B](#path-b--cache-core-plus-a-backend)) |
| Extra opt-in | None | `ExperimentalKotlinxIoCache` (`WARNING` level) |
| Choose it when | Almost always | You already depend on kotlinx-io and want to avoid Okio |

```kotlin
// Okio (default)
@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        fileSystem = OkioCacheFileSystem() // defaults to okio.FileSystem.SYSTEM
    }
}

// kotlinx-io (experimental)
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        fileSystem = KotlinxIoCacheFileSystem() // defaults to kotlinx.io.files.SystemFileSystem
    }
}
```

`KotlinxIoCacheFileSystem` tracks kotlinx-io's own Alpha-stability `kotlinx.io.files` package and may
change shape between minor versions of this library. **Okio is the recommended backend for
production.**

### Using the storage with Ktor `HttpCache`

If you already configure Ktor's own `HttpCache`, plug this library's storage into it without the
`PersistentCache` plugin. Each backend module exposes one factory that returns Ktor's `CacheStorage`
and needs no `@InternalPersistentCacheApi` opt-in:

```kotlin
import io.github.santimattius.persistent.cache.OkioCacheStorage
import io.ktor.client.plugins.cache.HttpCache

val client = HttpClient(CIO) {
    install(HttpCache) {
        publicStorage(OkioCacheStorage(maxSize = 20L * 1024 * 1024)) // or privateStorage(...)
    }
}
```

| Factory | Module | Stability |
|---|---|---|
| `OkioCacheStorage(...)` | `cache-okio` | Stable |
| `KotlinxIoCacheStorage(...)` | `cache-kotlinx-io` | Experimental, needs `@OptIn(ExperimentalKotlinxIoCache::class)` |

Both take the same named parameters, with the same defaults as `PersistentCacheConfig`:
`directory = "http_cache"`, `maxSize = 10 MiB`, `ttl = 1 hour`,
`directoryProvider = getCacheDirectoryProvider()`, the backend file system
(`okio.FileSystem.SYSTEM` / `SystemFileSystem`) and `clock`. They behave exactly like
`install(PersistentCache)` (atomic writes, TTL, LRU eviction; see [Storage behavior](docs/STORAGE.md)).

| Prefer | When |
|---|---|
| `install(PersistentCache)` | You want the shortest setup. It also wires `shared`/`public` and routes the storage to `publicStorage` or `privateStorage` for you. |
| The factory | You want full control over `HttpCache`: several storages, a custom `isShared`, your own plugin ordering. Ktor's routing rules (`Cache-Control: private` goes to `privateStorage`) are then yours to configure. |

Use **one storage instance per cache directory**.

### Custom cache directory

To control where the cache is stored (a custom folder, a test directory), implement
`CacheDirectoryProvider` and assign it to `directoryProvider`:

```kotlin
val customProvider = object : CacheDirectoryProvider {
    override val cacheDirectory: String get() = "/custom/cache/dir"
}

@OptIn(InternalPersistentCacheApi::class)
val client = HttpClient(CIO) {
    install(PersistentCache) {
        fileSystem = OkioCacheFileSystem()
        directoryProvider = customProvider
    }
}
```

Without a custom provider, `getCacheDirectoryProvider()` returns the platform implementation
(Android app cache dir, iOS caches dir or JVM temp dir). `cacheDirectory` is a plain `String`, not
an `okio.Path` — see the [migration guide](docs/MIGRATION.md) if you are upgrading a pre-1.2 custom
provider.

### Auth plugin + persistent cache

This library installs Ktor's `HttpCache` and does **not** intercept the Auth pipeline. When you also
install [Auth](https://ktor.io/docs/client-auth.html), behavior follows Ktor's cache routing:

- Authenticated responses need **`Cache-Control: private`** (for example `private, max-age=3600`) to
  be stored in **private** storage — the store configured by `install(PersistentCache)` when
  `public = false`. Responses with only `max-age` (no `private`) route to Ktor's default public
  storage, which this plugin does not configure.
- Set **`shared = false`** when using Bearer (or other) auth on the same client. Ktor skips cache
  lookup for authorized requests on a shared client and refuses to store private entries when
  `shared = true`.

These rules are verified by `AuthPluginInteropTest` in `cache-okio`, against the real
`install(PersistentCache)` DSL.

---

## How caching works (FAQ)

**Which responses are persisted, and for how long?**
This library persists whatever Ktor's `HttpCache` stores. Freshness comes from the origin server's
headers per [RFC 7234](https://www.rfc-editor.org/rfc/rfc7234) (`Cache-Control: max-age=...`,
`Expires`). Responses marked `no-store` are not written to disk.

**What does `ttl` do, then?**
It bounds how long an entry is *retained* on disk. Ktor's `HttpCache` already enforces HTTP freshness
from `CachedResponseData.expires`. See [Storage behavior](docs/STORAGE.md).

**What happens if the app crashes mid-write?**
Entries are written to a temporary file and moved into place atomically, so a crash cannot leave a
truncated entry. Stale `*.tmp` files are removed on startup and by `clear()`.

**Can two clients share one cache directory?**
Use one storage instance per directory. Concurrency guarantees are described in
[Storage behavior](docs/STORAGE.md).

**Does upgrading invalidate my cache?**
From `2.0.0-alpha02`, entry file names and format changed, and files written by earlier versions are
removed at startup. The only effect is cache misses while the cache refills. Switching between the
Okio and kotlinx-io backends does **not** invalidate the cache.

---

## Architecture

Four published modules:

| Module | Artifact | Contains |
|---|---|---|
| [`cache-core`](cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache) | `ktor-persistent-cache-core` | The `install(PersistentCache)` DSL, the shared caching engine (`FileCacheStorage`), the [`CacheFileSystem`](cache-core/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheFileSystem.kt) backend SPI and `CacheDirectoryProvider`. Zero I/O dependencies. |
| `cache-okio` | `ktor-persistent-cache-okio` | `OkioCacheFileSystem` and `OkioCacheStorage` — the default, stable backend. |
| `cache-kotlinx-io` | `ktor-persistent-cache-kotlinx-io` | `KotlinxIoCacheFileSystem` and `KotlinxIoCacheStorage` — the experimental, opt-in backend. |
| `shared` | `ktor-persistent-cache` | A facade depending on `cache-core` + `cache-okio`, keeping the pre-1.2 Maven coordinate working. |

Unpublished modules: `cache-test-suite` (shared conformance tests), `sample` (a KMP sample
verifying persistence with both backends), `androidApp` and `iosApp` (native shells for the sample).

---

## Documentation

| Document | Read it to |
|---|---|
| [Storage behavior](docs/STORAGE.md) | Understand the on-disk layout, eviction, concurrency and `ttl` vs. `expires`. |
| [Migration guide](docs/MIGRATION.md) | Upgrade from a pre-1.2 version. |
| [CHANGELOG](CHANGELOG.md) | See what changed in each release. |
| [Publishing](docs/PUBLISHING.md) | Publish to Maven Central (maintainers). |
| [Ktor — Caching](https://ktor.io/docs/client-caching.html) | Learn the `HttpCache` plugin this library builds on. |

---

## Building from source

```bash
./gradlew build                  # build all modules
./gradlew check apiCheck         # tests and public API checks
./gradlew publishToMavenLocal    # then use mavenLocal() in your project
```

### Running the sample apps

The unpublished `:sample` module has native Android and iOS shells. Each has independent **Okio** and
**kotlinx-io** actions that run an offline `MockEngine` persistence scenario.

- **Android:** open `androidApp` in Android Studio and run the `androidApp` configuration, or run
  `./gradlew :androidApp:installDebug` with a connected device.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme. The build phase
  compiles the `KtorPersistentCacheSample` framework from `:sample` automatically.

**Pass** means the first client made one offline engine request, while a fresh second client made
zero engine requests and returned the persisted response — real filesystem persistence, with no
network dependency.

---

## Contributing, security and support

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening an issue or pull
request, and follow the [Code of Conduct](CODE_OF_CONDUCT.md). To report a vulnerability, see
[SECURITY.md](SECURITY.md); for usage questions, see [SUPPORT.md](SUPPORT.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
