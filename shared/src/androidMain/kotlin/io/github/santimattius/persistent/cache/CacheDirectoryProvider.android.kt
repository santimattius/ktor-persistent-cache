package io.github.santimattius.persistent.cache

import android.content.Context
import io.github.santimattius.persistent.cache.startup.getApplicationContext
import okio.Path
import okio.Path.Companion.toPath

/**
 * Android implementation: returns a [CacheDirectoryProvider] that uses the application's cache directory.
 *
 * Requires [io.github.santimattius.persistent.cache.startup.ContextInitializer] (or manual context injection)
 * to be initialized so [getApplicationContext] is available.
 */
actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return AndroidCacheDirectoryProvider(getApplicationContext())
}

/**
 * Provides the Android application cache directory as the cache root path.
 */
private class AndroidCacheDirectoryProvider(
    private val applicationContext: Context
) : CacheDirectoryProvider {
    override val cacheDirectory: Path
        get() {
            return applicationContext.cacheDir.absolutePath.toPath()
        }
}