package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.util.date.getTimeMillis
import okio.FileSystem
import okio.SYSTEM

/**
 * Factory for creating [CacheStorage] instances using okio-based file storage.
 *
 * Delegates to `cache-core`'s [FileCacheStorage] over [OkioCacheFileSystem] (design decision #6,
 * Engram #1505) — the same algorithm previously implemented directly in `OkioFileCacheStorage`,
 * which is removed in this PR (task 2.5).
 */
@Deprecated(
    message = "CacheStorageFactory built a CacheStorage directly from CacheConfig plus an Okio " +
        "FileSystem. install(PersistentCache) { ... } configures the same FileCacheStorage " +
        "algorithm (now in :cache-core) through one DSL, so most callers should install the " +
        "plugin instead of building CacheStorage by hand. No automatic replacement is offered " +
        "on the object itself — see create()'s own deprecation for a mechanical replacement of " +
        "the one member that is actually callable. See docs/MIGRATION.md."
)
object CacheStorageFactory {

    /**
     * Creates a new [CacheStorage] instance configured with the provided parameters.
     *
     * @param config The [CacheConfig] defining the cache behavior like size and TTL.
     * @param fileSystem The [FileSystem] to use for file operations. Defaults to [FileSystem.SYSTEM].
     * @param clock A function that returns the current time in milliseconds. Defaults to [getTimeMillis].
     * @param cacheDirectoryProvider Provider for the directory where the cache will be stored.
     * Defaults to the platform-specific cache directory.
     * @return A [CacheStorage] implementation that persists data to the file system.
     */
    @Suppress("DEPRECATION")
    @OptIn(InternalPersistentCacheApi::class)
    @Deprecated(
        message = "Builds exactly the FileCacheStorage this function always built, just without " +
            "the CacheConfig/CacheStorageFactory indirection. Prefer install(PersistentCache) " +
            "{ ... } unless you specifically need a raw CacheStorage to hand to your own " +
            "HttpCache setup. See docs/MIGRATION.md.",
        replaceWith = ReplaceWith(
            "FileCacheStorage(fileSystem = OkioCacheFileSystem(fileSystem), " +
                "directoryRoot = cacheDirectoryProvider.cacheDirectory, " +
                "directoryName = config.cacheDirectory, maxSize = config.maxCacheSize, " +
                "ttl = config.cacheTtl, clock = clock)",
            "io.github.santimattius.persistent.cache.FileCacheStorage",
            "io.github.santimattius.persistent.cache.OkioCacheFileSystem"
        )
    )
    fun create(
        config: CacheConfig,
        fileSystem: FileSystem = FileSystem.SYSTEM,
        clock: () -> Long = { getTimeMillis() },
        cacheDirectoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider()
    ): CacheStorage = FileCacheStorage(
        fileSystem = OkioCacheFileSystem(fileSystem),
        directoryRoot = cacheDirectoryProvider.cacheDirectory,
        directoryName = config.cacheDirectory,
        maxSize = config.maxCacheSize,
        ttl = config.cacheTtl,
        clock = clock
    )
}
