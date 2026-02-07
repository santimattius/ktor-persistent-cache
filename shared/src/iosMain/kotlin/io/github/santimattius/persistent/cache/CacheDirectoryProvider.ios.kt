package io.github.santimattius.persistent.cache

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation: returns a [CacheDirectoryProvider] that uses the app's caches directory in the sandbox.
 */
actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return IosCacheDirectoryProvider()
}

/**
 * Provides the iOS app caches directory (NSCachesDirectory in the user domain) as the cache root path.
 */
private class IosCacheDirectoryProvider : CacheDirectoryProvider {
    @OptIn(ExperimentalForeignApi::class)
    override val cacheDirectory: Path
        get() {
            val cacheDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            return requireNotNull(requireNotNull(cacheDirectory).path).toPath()
        }

}