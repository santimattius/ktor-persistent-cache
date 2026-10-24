package io.github.santimattius.persistent.cache

/**
 * Default values shared by [PersistentCacheConfig] and the public `CacheStorage` factories of the
 * backend modules (`OkioCacheStorage`, `KotlinxIoCacheStorage`), so every entry point agrees.
 *
 * Public only because Kotlin has no cross-module `internal`; application code should rely on the
 * documented defaults of the factories and of [PersistentCacheConfig] instead.
 */
@InternalPersistentCacheApi
public object CacheStorageDefaults {

    /** Default subdirectory name under the cache root. */
    public const val DIRECTORY: String = "http_cache"

    /** Default maximum on-disk size in bytes (10 MiB). */
    public const val MAX_SIZE: Long = 10L * 1024 * 1024

    /** Default entry time-to-live in milliseconds (1 hour). */
    public const val TTL: Long = 60L * 60 * 1000
}
