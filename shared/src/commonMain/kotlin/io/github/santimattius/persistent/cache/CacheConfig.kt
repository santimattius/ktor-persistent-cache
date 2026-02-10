package io.github.santimattius.persistent.cache

/**
 * Configuration for the persistent HTTP cache.
 *
 * Use this to control where and how responses are stored, how long they live,
 * and whether the cache is shared or public (affects cacheability and reuse).
 *
 * @property enabled Whether caching is enabled (default: false).
 * @property cacheDirectory Custom cache directory name (default: "http_cache").
 * @property maxCacheSize Maximum cache size in bytes (default: 10MB).
 * @property cacheTtl Time-to-live for cache entries in milliseconds (default: 1 hour).
 * @property isShared Whether the cache is shared across requests (default: true).
 * @property isPublic Whether cached responses are considered public (default: false).
 *   When true, responses may be shared across users; when false, they are private to the client.
 */
data class CacheConfig(
    val enabled: Boolean = false,
    val cacheDirectory: String = "http_cache",
    val maxCacheSize: Long = 10L * 1024 * 1024, // 10 MB
    val cacheTtl: Long = 60 * 60 * 1000, // 1 hour
    val isShared: Boolean = true,
    val isPublic: Boolean = false
) {
    /**
     * Creates a [CacheConfig] with only [enabled] and [cacheDirectory];
     * other properties use their defaults.
     *
     * @param enable Whether caching is enabled.
     * @param cacheDirectory Custom cache directory name.
     */
    constructor(enable: Boolean, cacheDirectory: String) : this(
        enabled = enable,
        cacheDirectory = cacheDirectory
    )
}