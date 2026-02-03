package io.github.santimattius.persistent.cache

import okio.Path

/**
 * Provides access to the platform-specific cache directory.
 *
 * This interface is used to abstract the retrieval of the cache directory path,
 * allowing for different implementations on various platforms (e.g., Android, iOS, JVM).
 */
interface CacheDirectoryProvider {

    /**
     * Provides the cache directory path for the current platform
     */
    val cacheDirectory: Path
}

expect fun getCacheDirectoryProvider(): CacheDirectoryProvider