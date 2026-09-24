package io.github.santimattius.persistent.cache

/**
 * Marks backend-SPI declarations ([CacheFileSystem], [FileCacheStorage]) that exist to let
 * out-of-tree filesystem backends (`cache-okio`, `cache-kotlinx-io`, or a custom one) plug into
 * `cache-core`. They are public (Kotlin has no cross-module `internal`), but not a supported,
 * stable API for application code — this is the same precedent as Ktor's own `@InternalAPI`.
 *
 * Opting in acknowledges the SPI can change between minor versions without the usual
 * binary-compatibility guarantee.
 */
@RequiresOptIn(
    message = "This is a backend SPI for implementing a CacheFileSystem. It is not a supported, " +
        "stable API for application code and may change between minor versions.",
    level = RequiresOptIn.Level.ERROR
)
public annotation class InternalPersistentCacheApi
