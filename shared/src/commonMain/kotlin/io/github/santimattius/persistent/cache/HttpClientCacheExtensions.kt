package io.github.santimattius.persistent.cache

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage

/**
 * Installs persistent file-based HTTP caching on this client using [HttpCache].
 *
 * When [CacheConfig.enabled] is true, responses are stored on disk via [CacheStorageFactory]
 * (delegating to `cache-core` + `cache-okio`, design decision #6, Engram #1505) under the
 * directory supplied by [cacheDirectoryProvider], respecting [CacheConfig.maxCacheSize]
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
@Suppress("DEPRECATION")
@Deprecated(
    message = "installPersistentCache built CacheConfig into a CacheStorageFactory-backed " +
        "CacheStorage before installing Ktor's HttpCache. install(PersistentCache) { ... } does " +
        "the same thing through one client plugin DSL — same algorithm, one type, no field " +
        "renaming across layers. A backend (e.g. cache-okio's OkioCacheFileSystem) must be " +
        "assigned to `fileSystem` explicitly, since cache-core ships no default backend; that " +
        "assignment requires @OptIn(InternalPersistentCacheApi::class) on the enclosing " +
        "declaration. If `config.enabled` was false, omit install(PersistentCache) entirely " +
        "instead — there is no direct 'disabled' toggle. See docs/MIGRATION.md.",
    replaceWith = ReplaceWith(
        "install(PersistentCache) { directory = config.cacheDirectory; " +
            "maxSize = config.maxCacheSize; ttl = config.cacheTtl; shared = config.isShared; " +
            "public = config.isPublic; directoryProvider = cacheDirectoryProvider; " +
            "fileSystem = OkioCacheFileSystem() }",
        "io.github.santimattius.persistent.cache.PersistentCache",
        "io.github.santimattius.persistent.cache.OkioCacheFileSystem"
    )
)
fun HttpClientConfig<*>.installPersistentCache(
    config: CacheConfig,
    cacheDirectoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider()
) {
    val storage = if (config.enabled) {
        CacheStorageFactory.create(config, cacheDirectoryProvider = cacheDirectoryProvider)
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
