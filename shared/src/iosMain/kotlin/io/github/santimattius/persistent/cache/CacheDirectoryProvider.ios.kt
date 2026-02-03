package io.github.santimattius.persistent.cache

import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
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
            val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            return requireNotNull(requireNotNull(documentDirectory).path).toPath()
        }

}