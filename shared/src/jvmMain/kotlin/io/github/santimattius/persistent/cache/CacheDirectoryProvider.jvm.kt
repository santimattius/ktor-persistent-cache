package io.github.santimattius.persistent.cache

import okio.Path
import okio.Path.Companion.toPath
import java.io.File

/**
 * JVM implementation: returns a [CacheDirectoryProvider] that uses a directory under the system temp directory
 * (e.g. `java.io.tmpdir/ktor-cache`).
 */
actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return JvmCacheDirectoryProvider()
}

/**
 * Provides a cache directory under the JVM temp directory (`java.io.tmpdir/ktor-cache`).
 * The directory is created if it does not exist.
 */
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
