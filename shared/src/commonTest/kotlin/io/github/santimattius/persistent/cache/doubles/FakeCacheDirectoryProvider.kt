package io.github.santimattius.persistent.cache.doubles

import io.github.santimattius.persistent.cache.CacheDirectoryProvider
import okio.Path.Companion.toPath
import okio.Path

/**
 * A fake CacheDirectoryProvider for testing purposes.
 * Allows tests to specify a custom cache directory path.
 */
class FakeCacheDirectoryProvider(
    private val basePath: String = "/fake/cache"
) : CacheDirectoryProvider {

    override val cacheDirectory: Path
        get() = basePath.toPath()
}
