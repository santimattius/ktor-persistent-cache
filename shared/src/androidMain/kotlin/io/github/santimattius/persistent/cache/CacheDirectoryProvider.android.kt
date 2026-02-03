package io.github.santimattius.persistent.cache

import android.content.Context
import io.github.santimattius.persistent.cache.startup.getApplicationContext
import okio.Path
import okio.Path.Companion.toPath

actual fun getCacheDirectoryProvider(): CacheDirectoryProvider {
    return AndroidCacheDirectoryProvider(getApplicationContext())
}

private class AndroidCacheDirectoryProvider(
    private val applicationContext: Context
) : CacheDirectoryProvider {
    override val cacheDirectory: Path
        get() {
            return applicationContext.cacheDir.absolutePath.toPath()
        }

}