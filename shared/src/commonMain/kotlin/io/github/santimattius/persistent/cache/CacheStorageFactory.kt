package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.util.date.getTimeMillis
import okio.FileSystem
import okio.SYSTEM

/**
 * Factory for creating [CacheStorage] instances using okio-based file storage.
 */
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
    fun create(
        config: CacheConfig,
        fileSystem: FileSystem = FileSystem.SYSTEM,
        clock: () -> Long = { getTimeMillis() },
        cacheDirectoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider()
    ): CacheStorage = OkioFileCacheStorage(
        config = OkioFileCacheConfig(
            fileName = config.cacheDirectory,
            maxSize = config.maxCacheSize,
            ttl = config.cacheTtl,
            cacheDirectoryProvider = cacheDirectoryProvider
        ),
        fileSystem = fileSystem,
        clock = clock
    )
}