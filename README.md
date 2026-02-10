# Ktor Persistent Cache

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.3.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Ktor](https://img.shields.io/badge/Ktor-3.4.0-000000?logo=ktor&logoColor=white)](https://ktor.io)
[![Android](https://img.shields.io/badge/Android-AGP%209.0-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-Supported-8E8E93?logo=apple&logoColor=white)](https://developer.apple.com)
[![JVM](https://img.shields.io/badge/JVM-Supported-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)

A **Kotlin Multiplatform** library that adds **persistent HTTP caching** to [Ktor](https://ktor.io)
HttpClient. Responses are stored on disk using [Okio](https://github.com/square/okio), with
configurable size limits, TTL, and platform-appropriate cache directories.

---

## Features

- **Persistent storage** — Cache survives app restarts; stored on the filesystem via Okio.
- **Kotlin Multiplatform** — Shared API for **Android**, **iOS**, and **JVM**.
- **Ktor integration** — Uses Ktor’s [HttpCache](https://ktor.io/docs/client-caching.html) plugin;
  you configure cache storage and options in one place.
- **Configurable** — TTL (time-to-live), max cache size, directory name, shared vs unshared, and
  public vs private storage.
- **Content negotiation** — Respects `Vary` headers so different variants (e.g. by
  `Accept-Language`) are cached separately.
- **LRU eviction** — When the cache exceeds the configured size, least-recently-used entries are
  removed.
- **Custom cache location** — Optional [CacheDirectoryProvider] for custom cache root paths (e.g.
  for tests or special directories).

[CacheDirectoryProvider]: #custom-cache-directory

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

Add the dependency to your shared or platform source sets.

**Kotlin DSL (Gradle):**

```kotlin
repositories {
    mavenCentral()
    // For snapshots:
    // maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    commonMain.dependencies {
        implementation("io.github.santimattius:ktor-persistent-cache:1.0.0-SNAPSHOT")
    }
    // Also add a Ktor engine for each target, e.g.:
    // implementation("io.ktor:ktor-client-okhttp")   // Android
    // implementation("io.ktor:ktor-client-cio")     // iOS / JVM
}
```

**Version catalog (e.g. `libs.versions.toml`):**

```toml
[versions]
ktorPersistentCache = "1.0.0-SNAPSHOT"

[libraries]
ktor-persistent-cache = { group = "io.github.santimattius", name = "ktor-persistent-cache", version.ref = "ktorPersistentCache" }
```

Then:

```kotlin
implementation(libs.ktor.persistent.cache)
```

---

## Setup

### Android

The library needs the **application context** to resolve the cache directory. The recommended way is
**App Startup**:

1. **Merge the library’s manifest**  
   The `shared` (or Android) module that depends on `ktor-persistent-cache` should merge the
   library’s AndroidManifest so that the App Startup `InitializationProvider` and
   `ContextInitializer` are registered.

2. **No extra code**  
   If the manifest is merged, `ContextInitializer` runs at app startup and injects the application
   context. [getCacheDirectoryProvider()](shared/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheDirectoryProvider.kt)
   will then use it automatically.

If you **don’t** use the library’s manifest (e.g. you use a different DI or startup path), you must
call **once** at app startup with the **application context** (not an Activity context):

```kotlin
// e.g. in Application.onCreate()
injectContext(applicationContext)
```

`injectContext` is provided by the library package
`io.github.santimattius.persistent.cache.startup`.

### iOS

No setup. The library uses the default app caches directory.

### JVM

No setup. The library uses a subdirectory of the JVM temp directory.

---

## Quick start

1. Create an [HttpClient](https://ktor.io/docs/create-client.html) and call **installPersistentCache
   ** with a [CacheConfig]:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.github.santimattius.persistent.cache.*

val client = HttpClient(CIO) {
    installPersistentCache(
        CacheConfig(
            enabled = true,
            cacheDirectory = "http_cache",
            maxCacheSize = 10L * 1024 * 1024, // 10 MB
            cacheTtl = 60 * 60 * 1000,       // 1 hour
            isShared = true,
            isPublic = false
        )
    )
}
```

2. Use the client as usual. The cache stores responses for requests that support caching and serves
   them when valid.

```kotlin
val response: String = client.get("https://example.com/api/data").body()
```

3. Optionally pass a custom [CacheDirectoryProvider] as the second parameter (
   see [Custom cache directory](#custom-cache-directory)).

---

## Configuration

[CacheConfig] supports:

| Property         | Type      | Default        | Description                                                                                                                 |
|------------------|-----------|----------------|-----------------------------------------------------------------------------------------------------------------------------|
| `enabled`        | `Boolean` | `false`        | Whether the HTTP cache is enabled.                                                                                          |
| `cacheDirectory` | `String`  | `"http_cache"` | Name of the cache directory under the platform cache root.                                                                  |
| `maxCacheSize`   | `Long`    | 10 MB          | Maximum cache size in bytes. LRU eviction when exceeded. Use `0` for no limit.                                              |
| `cacheTtl`       | `Long`    | 1 hour         | Time-to-live for entries in milliseconds.                                                                                   |
| `isShared`       | `Boolean` | `true`         | Whether the cache is shared across requests (Ktor [HttpCache](https://ktor.io/docs/client-caching.html) behavior).          |
| `isPublic`       | `Boolean` | `false`        | When `true`, cached responses are treated as public (shareable across users); when `false`, they are private to the client. |

Convenience constructor:

```kotlin
CacheConfig(enabled = true, cacheDirectory = "my_cache")
// Other properties use defaults.
```

[CacheConfig]: shared/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheConfig.kt

---

## Custom cache directory

To control where the cache is stored (e.g. a custom folder or test directory),
implement [CacheDirectoryProvider] and pass it to **installPersistentCache**:

```kotlin
val customProvider = object : CacheDirectoryProvider {
    override val cacheDirectory: Path get() = FileSystem.SYSTEM.toPath("/custom/cache/dir")
}

HttpClient(CIO) {
    installPersistentCache(
        config = CacheConfig(enabled = true, cacheDirectory = "http_cache"),
        cacheDirectoryProvider = customProvider
    )
}
```

Default behavior (no custom provider): [getCacheDirectoryProvider()] returns the platform
implementation (Android app cache dir, iOS caches dir, or JVM temp dir).

[getCacheDirectoryProvider()]: shared/src/commonMain/kotlin/io/github/santimattius/persistent/cache/CacheDirectoryProvider.kt

---

## Building and testing (from source)

**Build:**

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileAndroidMain
# or
./gradlew :shared:assemble
```

**Run tests:**

```bash
./gradlew test
```

**Publish to local Maven:**

```bash
./gradlew :shared:publishToMavenLocal
```

Then depend on `io.github.santimattius:ktor-persistent-cache:1.0.0-SNAPSHOT` with `mavenLocal()` in
your project.

---

## Publishing

The `shared` module is published with
the [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).

| Action                   | Command / Doc                                                           |
|--------------------------|-------------------------------------------------------------------------|
| Publish to local Maven   | `./gradlew :shared:publishToMavenLocal`                                 |
| Publish to Maven Central | See [docs/PUBLISHING.md](docs/PUBLISHING.md) for credentials and steps. |

Coordinates and POM are configured in `shared/build.gradle.kts`.

---

## License

This project is licensed under
the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

---

## References

| Resource               | URL                                                                                 |
|------------------------|-------------------------------------------------------------------------------------|
| Ktor — HTTP client     | [ktor.io/docs/client](https://ktor.io/docs/client.html)                             |
| Ktor — Caching         | [ktor.io/docs/client-caching](https://ktor.io/docs/client-caching.html)             |
| Okio                   | [github.com/square/okio](https://github.com/square/okio)                            |
| Kotlin Multiplatform   | [kotlinlang.org/docs/multiplatform](https://kotlinlang.org/docs/multiplatform.html) |
| Publishing (this repo) | [docs/PUBLISHING.md](docs/PUBLISHING.md)                                            |
