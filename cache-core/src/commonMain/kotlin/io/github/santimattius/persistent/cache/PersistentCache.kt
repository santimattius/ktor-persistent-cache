package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.util.date.getTimeMillis

/**
 * Configuration for the [PersistentCache] client plugin: one consolidated type, replacing the
 * pre-split `CacheConfig` / `CacheStorageConfig` / `OkioFileCacheConfig` trio with no field
 * renaming across layers.
 */
public class PersistentCacheConfig {

    /** Subdirectory name under the cache root. Default: `"http_cache"`. */
    public var directory: String = "http_cache"

    /** Maximum on-disk size in bytes. A value `<= 0` means unlimited. Default: 10 MB. */
    public var maxSize: Long = 10L * 1024 * 1024

    /** Entry time-to-live in milliseconds. A value `<= 0` means entries never expire. Default: 1 hour. */
    public var ttl: Long = 60 * 60 * 1000

    /** Whether the client where this is installed is shared among multiple users. Default: `true`. */
    public var shared: Boolean = true

    /** Whether cached responses are public (shared across users) rather than private. Default: `false`. */
    public var public: Boolean = false

    /**
     * The backend SPI used to persist cache entries. `cache-core` has no default backend: a
     * backend module (e.g. `cache-okio`, `cache-kotlinx-io`) or a custom [CacheFileSystem] must
     * supply one before `install(PersistentCache)` can build working storage.
     *
     * Declared as [CacheFileSystem]`<*>` (star-projected), not `CacheFileSystem<Any?>`: [P] is
     * genuinely invariant (it appears in both "in" and "out" positions across the SPI), so a
     * concrete backend like `cache-okio`'s `OkioCacheFileSystem : CacheFileSystem<okio.Path>`
     * could never be assigned to a property statically typed `CacheFileSystem<Any?>`. The star
     * projection accepts any concrete backend; [toCacheStorage] recovers type safety with an
     * unchecked cast that is sound because every `P` value flowing through [FileCacheStorage]
     * originates from, and is only ever handed back to, that same erased backend instance.
     */
    @InternalPersistentCacheApi
    public var fileSystem: CacheFileSystem<*>? = null

    /** Supplies the cache root directory. Defaults to the platform-specific [getCacheDirectoryProvider]. */
    public var directoryProvider: CacheDirectoryProvider? = null

    /** Supplies the current time in milliseconds. Defaults to [getTimeMillis]. */
    public var clock: () -> Long = { getTimeMillis() }
}

/**
 * Installs persistent file-based HTTP caching using Ktor's `HttpCache` plugin, configured via one
 * consolidated [PersistentCacheConfig] instead of `CacheConfig` / `CacheStorageConfig` /
 * `OkioFileCacheConfig`:
 *
 * ```kotlin
 * install(PersistentCache) {
 *     directory = "cache"
 *     maxSize = 10L * 1024 * 1024
 *     ttl = 3_600_000
 *     shared = false
 *     public = false
 * }
 * ```
 *
 * Built on `createClientPlugin`. Its setup body calls the real `HttpCache` plugin's
 * `prepare`/`install` directly on the already-constructed `HttpClient` (available as `client`
 * inside the plugin builder) — this is the supported way to compose a second real Ktor plugin
 * from inside a `createClientPlugin` body, since `HttpClientConfig.install` only *queues*
 * plugins at config-build time and cannot be invoked again once that queue is already being
 * applied to the client. This keeps `Cache-Control: private` / `isShared` routing behaviorally
 * identical to installing `HttpCache` directly (the cache-core-storage interop requirement).
 */
public val PersistentCache: ClientPlugin<PersistentCacheConfig> =
    createClientPlugin("PersistentCache", ::PersistentCacheConfig) {
        val config = pluginConfig
        val storage = config.toCacheStorage()

        val preparedHttpCache = HttpCache.prepare {
            isShared = config.shared
            if (config.public) {
                publicStorage(storage)
            } else {
                privateStorage(storage)
            }
        }
        HttpCache.install(preparedHttpCache, client)
    }

@OptIn(InternalPersistentCacheApi::class)
private fun PersistentCacheConfig.toCacheStorage(): CacheStorage {
    val backend = fileSystem
        ?: error(
            "PersistentCache requires a CacheFileSystem backend. Configure `fileSystem` " +
                "(e.g. cache-okio's OkioCacheFileSystem or cache-kotlinx-io's " +
                "KotlinxIoCacheFileSystem)."
        )
    val root = (directoryProvider ?: getCacheDirectoryProvider()).cacheDirectory
    return buildFileCacheStorage(backend, root, directory, maxSize, ttl, clock)
}

@Suppress("UNCHECKED_CAST")
@OptIn(InternalPersistentCacheApi::class)
private fun buildFileCacheStorage(
    fileSystem: CacheFileSystem<*>,
    root: String,
    directory: String,
    maxSize: Long,
    ttl: Long,
    clock: () -> Long
): CacheStorage = FileCacheStorage(
    fileSystem = fileSystem as CacheFileSystem<Any?>,
    directoryRoot = root,
    directoryName = directory,
    maxSize = maxSize,
    ttl = ttl,
    clock = clock
)
