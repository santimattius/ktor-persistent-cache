package io.github.santimattius.persistent.cache

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return IosCacheDirectoryProvider()
}

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