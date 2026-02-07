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
     * Provides the cache directory path for the current platform.
     */
    val cacheDirectory: Path
}

/**
 * Returns the platform-specific [CacheDirectoryProvider] used to resolve the cache directory path.
 *
 * On Android, uses the application cache directory.
 * On iOS, uses the app's caches directory in the sandbox.
 * On JVM, uses a directory under the system temp directory.
 *
 * @return The [CacheDirectoryProvider] for the current platform.
 */
expect fun getCacheDirectoryProvider(): CacheDirectoryProvider