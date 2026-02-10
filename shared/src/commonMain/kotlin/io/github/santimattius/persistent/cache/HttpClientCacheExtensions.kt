package io.github.santimattius.persistent.cache

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage

/**
 * Installs persistent file-based HTTP caching on this client using [HttpCache].
 *
 * When [CacheConfig.enabled] is true, responses are stored on disk via [OkioFileCacheStorage]
 * under the directory supplied by [cacheDirectoryProvider], respecting [CacheConfig.maxCacheSize]
 * and [CacheConfig.cacheTtl]. When false, the cache plugin is still installed but uses
 * [CacheStorage.Disabled], so no storage is used.
 *
 * Public vs private storage is controlled by [CacheConfig.isPublic]; shared vs unshared
 * by [CacheConfig.isShared].
 *
 * @param config Cache behavior and limits; see [CacheConfig].
 * @param cacheDirectoryProvider Supplies the root directory for the cache. Defaults to the
 *   platform-specific provider from [getCacheDirectoryProvider]; override for custom paths or tests.
 */
fun HttpClientConfig<*>.installPersistentCache(
    config: CacheConfig,
    cacheDirectoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider()
) {
    val storage = if (config.enabled) {
        OkioFileCacheStorage(
            config = OkioFileCacheConfig(
                fileName = config.cacheDirectory,
                maxSize = config.maxCacheSize,
                ttl = config.cacheTtl,
                cacheDirectoryProvider = cacheDirectoryProvider
            )
        )
    } else {
        CacheStorage.Disabled
    }
    install(HttpCache) {
        isShared = config.isShared
        if (config.isPublic) {
            publicStorage(storage)
        } else {
            privateStorage(storage)
        }
    }
}