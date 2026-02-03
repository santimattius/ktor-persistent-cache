package io.github.santimattius.persistent.cache

/**
 * Configuration for [OkioFileCacheStorage].
 *
 * @property fileName The base name for the cache directory.
 * @property maxSize The maximum size of the cache in bytes.
 * @property ttl The time-to-live for cache entries in milliseconds.
 * @property cacheDirectoryProvider The provider for the cache directory. If not provided, a default one will be used.
 */
data class OkioFileCacheConfig(
    var fileName: String = "http_cache",
    var maxSize: Long = 10L * 1024 * 1024, // 10 MB
    var ttl: Long = 60 * 60 * 1000, // 1 hour
    val cacheDirectoryProvider: CacheDirectoryProvider
)
