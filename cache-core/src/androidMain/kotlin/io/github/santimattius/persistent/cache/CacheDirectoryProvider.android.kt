package io.github.santimattius.persistent.cache

import android.content.Context
import io.github.santimattius.persistent.cache.startup.getApplicationContext

/**
 * Android implementation: returns a [CacheDirectoryProvider] that uses the application's cache directory.
 *
 * Requires [io.github.santimattius.persistent.cache.startup.ContextInitializer] (or manual context injection)
 * to be initialized so [getApplicationContext] is available.
 */
public actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return AndroidCacheDirectoryProvider(getApplicationContext())
}

/**
 * Provides the Android application cache directory as the cache root path.
 */
private class AndroidCacheDirectoryProvider(
    private val applicationContext: Context
) : CacheDirectoryProvider {
    override val cacheDirectory: String
        get() = applicationContext.cacheDir.absolutePath
}
