package io.github.santimattius.persistent.cache

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage

/**
 * Configures the HTTP client with caching support based on the provided configuration.
 *
 * @param config The cache configuration
 * @param cacheDirectoryProvider Optional custom cache directory provider
 */
fun HttpClientConfig<*>.configureCache(
    config: CacheConfig,
    cacheDirectoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider()
) {
    if (!config.enabled) return
    install(HttpCache) {
        isShared = config.isShared
        val storage = OkioFileCacheStorage(
            config = OkioFileCacheConfig(
                fileName = config.cacheDirectory,
                maxSize = config.maxCacheSize,
                ttl = config.cacheTtl,
                cacheDirectoryProvider = cacheDirectoryProvider
            )
        )
        privateStorage(storage)
    }
}

/**
 * Disables caching for this client.
 */
internal fun HttpClientConfig<*>.disableCaching() {
    install(HttpCache) {
        privateStorage(CacheStorage.Disabled)
    }
}
