package io.github.santimattius.persistent.cache

import okio.Path
import okio.Path.Companion.toPath
import java.io.File

actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return JvmCacheDirectoryProvider()
}

private class JvmCacheDirectoryProvider : CacheDirectoryProvider {
    override val cacheDirectory: Path
        get() {
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "ktor-cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return cacheDir.absolutePath.toPath()
        }
}
