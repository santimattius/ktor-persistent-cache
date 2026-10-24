# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Cache entries are now written atomically (temporary file plus atomic move), so a crash can no longer leave a truncated entry. Stale `*.tmp` files are removed on startup and by `clear()`.
- The vary-key part of the file name is now the first 16 hex chars of a SHA-256 instead of a 32-bit `String.hashCode`, and `find` rejects entries whose stored vary keys differ from the request.
- Eviction no longer reads and decodes every entry after each `store`: it uses an in-memory index, and reads now refresh recency, so eviction is real least-recently-used.
- `KotlinxIoCacheFileSystem.writeAtomically` no longer fails on platforms where `atomicMove` is unsupported (Android API 24-25, where it falls back to a non-atomic write) and retries after deleting the destination if the move is refused.

### Changed

- Entry files now start with a magic and format-version header; files with a missing or unknown header are deleted.
- **Existing caches are discarded on first start.** Entry file names and format changed, and files written by earlier versions are removed at startup. The only consequence is cache misses while the cache refills.
- Added `CacheFileSystem.writeAtomically` (no default body) to the `@InternalPersistentCacheApi` backend SPI; custom backends must implement it.
- Documented storage concurrency (one instance per directory), eviction and expiry in `docs/STORAGE.md`. Ktor's `HttpCache` already enforces HTTP freshness from `CachedResponseData.expires`; the `ttl` option only bounds retention.

### Added

- Android host tests (`testAndroidHostTest`) now run the common tests and conformance suites for `:cache-core`, `:cache-okio` and `:cache-kotlinx-io` as part of `check`. They run on the JVM against Android stubs, not on a device or emulator.
- Added an unpublished Kotlin Multiplatform `sample` module that verifies persistent cache reads with both Okio and kotlinx-io.
- Added native Android Compose and iOS SwiftUI sample screens with visible pass/fail results.
- Added deterministic, offline backend checks using Ktor `MockEngine` across Android, JVM, and iOS targets.

## [2.0.0-alpha01] - 2026-10-21

### Added

- Added the backend-independent `cache-core` module with the persistent caching algorithm and `CacheFileSystem` SPI.
- Added the stable `cache-okio` backend and experimental `cache-kotlinx-io` backend.
- Added the idiomatic `install(PersistentCache) { ... }` Ktor client plugin DSL.
- Added binary compatibility validation for publishable modules.
- Added a migration guide and community contribution, conduct, security, and support documentation.

### Changed

- Converted `shared` into a compatibility facade over `cache-core` and `cache-okio`.
- Centralized the published artifact version in `gradle.properties`.
- Updated Kotlin to 2.4.20, Ktor to 3.6.0, Okio to 3.18.2, and Gradle to 9.8.0.

### Fixed

- Added persistent cache clearing across stored entries.
- Fixed Okio `SYSTEM` resolution during common metadata compilation.

### Breaking changes

- Changed `CacheDirectoryProvider.cacheDirectory` from an Okio path to `String` so `cache-core` remains independent of a filesystem library. See [`docs/MIGRATION.md`](docs/MIGRATION.md).

## [1.2.0] - 2026-07-04

### Added

- Added real-filesystem persistence coverage across cache instances.
- Added authenticated-request interoperability coverage and documented cache-header requirements.
- Exposed Android context injection for applications that do not use automatic startup initialization.

### Fixed

- Fixed Android cache persistence by retaining the application context.
- Fixed infinite-TTL handling.

### Changed

- Updated Kotlin to 2.4.0, Ktor to 3.5.1, Gradle to 9.6.1, and the Maven Publish plugin to 0.37.0.

## [1.1.0] - 2026-05-05

### Added

- Added `CacheStorageConfig` and `CacheStorageFactory` for constructing configured persistent cache storage.

### Changed

- Updated Kotlin to 2.3.21, Ktor to 3.4.3, and Gradle to 9.5.0.

## [1.0.1] - 2026-04-13

### Changed

- Promoted the SHA-256 cache-key changes from the preceding alpha release to stable.

## [1.0.1-ALPHA01] - 2026-04-08

### Changed

- Replaced URL-derived filenames with SHA-256 cache keys.

### Added

- Added long-URL coverage and an injectable test clock.

### Changed

- Updated Kotlin to 2.3.20, Ktor to 3.4.2, Okio to 3.17.0, and Gradle to 9.4.1.

## [1.0.0] - 2026-02-16

### Changed

- Published the first stable release of the persistent cache library.

## [1.0.0-ALPHA02] - 2026-02-10

### Changed

- Refined persistent cache installation and updated the usage documentation.

## [1.0.0-ALPHA01] - 2026-02-07

### Added

- Added persistent HTTP response caching backed by Okio.
- Added Android, iOS, and JVM cache-directory providers.
- Added vary-key support, eager initialization, cleanup behavior, and filesystem tests.
- Added JVM support and Maven Central publishing configuration.
- Added `lastModifiedAtMillis` persistence metadata.

[Unreleased]: https://github.com/santimattius/ktor-persistent-cache/compare/2.0.0-alpha01...HEAD
[2.0.0-alpha01]: https://github.com/santimattius/ktor-persistent-cache/compare/1.2.0...2.0.0-alpha01
[1.2.0]: https://github.com/santimattius/ktor-persistent-cache/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/santimattius/ktor-persistent-cache/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/santimattius/ktor-persistent-cache/compare/1.0.1-ALPHA01...1.0.1
[1.0.1-ALPHA01]: https://github.com/santimattius/ktor-persistent-cache/compare/1.0.0...1.0.1-ALPHA01
[1.0.0]: https://github.com/santimattius/ktor-persistent-cache/compare/1.0.0-ALPHA02...1.0.0
[1.0.0-ALPHA02]: https://github.com/santimattius/ktor-persistent-cache/compare/1.0.0-ALPHA01...1.0.0-ALPHA02
[1.0.0-ALPHA01]: https://github.com/santimattius/ktor-persistent-cache/releases/tag/1.0.0-ALPHA01
