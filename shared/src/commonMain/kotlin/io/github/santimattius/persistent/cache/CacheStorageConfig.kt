package io.github.santimattius.persistent.cache

/**
 * Shared shape for persistent HTTP cache limits: subdirectory name under the cache root,
 * maximum on-disk size, and entry TTL. Used by [CacheConfig] and [CacheStorageFactory].
 */
interface CacheStorageConfig {
    /**
     * The name of the directory where the cache will be stored (under the provider root).
     */
    val cacheDirectory: String
        get() = "http_cache"

    /**
     * The maximum size of the cache in bytes.
     */
    val maxCacheSize: Long
        get() = 10L * 1024 * 1024 // 10 MB

    /**
     * The time-to-live for cached entries in milliseconds.
     */
    val cacheTtl: Long
        get() = 60 * 60 * 1000 // 1 hour

    companion object {
        /**
         * Creates a default implementation of [CacheStorageConfig].
         *
         * @param cacheDirectory The name of the cache directory.
         * @param maxCacheSize The maximum size of the cache in bytes.
         * @param cacheTtl The time-to-live for cached entries in milliseconds.
         */
        fun default(
            cacheDirectory: String = "http_cache",
            maxCacheSize: Long = 10L * 1024 * 1024, // 10 MB
            cacheTtl: Long = 60 * 60 * 1000 // 1 hour
        ): CacheStorageConfig = object : CacheStorageConfig {
            override val cacheDirectory: String = cacheDirectory
            override val maxCacheSize: Long = maxCacheSize
            override val cacheTtl: Long = cacheTtl
        }
    }
}
