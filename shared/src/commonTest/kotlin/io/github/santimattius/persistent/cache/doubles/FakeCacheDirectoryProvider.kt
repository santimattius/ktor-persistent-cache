package io.github.santimattius.persistent.cache.doubles

import io.github.santimattius.persistent.cache.CacheDirectoryProvider

/**
 * A fake CacheDirectoryProvider for testing purposes.
 * Allows tests to specify a custom cache directory path.
 *
 * Implements `cache-core`'s [CacheDirectoryProvider] (returns [String], not `okio.Path`):
 * `:shared`'s own copy was deleted in this PR (task 2.7, accepted hard break, Engram #1506) and
 * this FQN now resolves to `cache-core`'s version via `:shared`'s `api(projects.cacheCore)`
 * dependency.
 */
class FakeCacheDirectoryProvider(
    private val basePath: String = "/fake/cache"
) : CacheDirectoryProvider {

    override val cacheDirectory: String
        get() = basePath
}
