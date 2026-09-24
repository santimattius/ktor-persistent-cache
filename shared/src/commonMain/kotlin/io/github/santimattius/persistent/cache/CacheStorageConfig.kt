package io.github.santimattius.persistent.cache

/**
 * Shared shape for persistent HTTP cache limits: subdirectory name under the cache root,
 * maximum on-disk size, and entry TTL. Used by [CacheConfig] and [CacheStorageFactory].
 */
@Deprecated(
    message = "CacheStorageConfig existed only so CacheConfig could implement one shared shape " +
        "for directory/maxSize/ttl. PersistentCacheConfig, configured inside " +
        "install(PersistentCache) { ... }, replaces both. No automatic replacement is offered " +
        "here: CacheStorageConfig is used as a supertype/interface, not a constructible " +
        "expression, so there is no single call site an IDE quick-fix could safely rewrite. " +
        "See docs/MIGRATION.md for the field-by-field mapping."
)
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
        @Suppress("DEPRECATION")
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
