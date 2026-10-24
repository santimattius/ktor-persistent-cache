package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.util.date.getTimeMillis
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem

/**
 * Creates a persistent, kotlinx-io-backed [CacheStorage] for Ktor's `HttpCache`, without
 * installing the [PersistentCache] plugin and without depending on Okio:
 *
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(HttpCache) {
 *         publicStorage(KotlinxIoCacheStorage(maxSize = 20L * 1024 * 1024))
 *     }
 * }
 * ```
 *
 * Experimental because kotlinx-io's `kotlinx.io.files` API is Alpha upstream; see
 * [ExperimentalKotlinxIoCache]. Use `OkioCacheStorage` from `cache-okio` for the stable backend.
 *
 * The storage is the same one `install(PersistentCache)` builds: atomic writes, collision-free
 * file names with a format version, TTL and least-recently-used eviction. See `docs/STORAGE.md`.
 * Use one storage instance per cache directory; two instances writing to the same directory are
 * unsupported.
 *
 * @param directory Subdirectory name under the cache root. Default: `"http_cache"`.
 * @param maxSize Maximum on-disk size in bytes. A value `<= 0` means unlimited. Default: 10 MiB.
 * @param ttl Entry time-to-live in milliseconds, measured from when the entry was stored. A value
 *   `<= 0` means entries never expire. Default: 1 hour. HTTP freshness is still enforced by Ktor.
 * @param directoryProvider Supplies the cache root directory. Defaults to the platform-specific
 *   [getCacheDirectoryProvider].
 * @param fileSystem The kotlinx-io [FileSystem] used for all I/O. Defaults to [SystemFileSystem].
 * @param clock Supplies the current time in milliseconds. Override for deterministic tests.
 * @return A [CacheStorage] to pass to `publicStorage(...)` or `privateStorage(...)`.
 */
@ExperimentalKotlinxIoCache
@OptIn(InternalPersistentCacheApi::class)
public fun KotlinxIoCacheStorage(
    directory: String = CacheStorageDefaults.DIRECTORY,
    maxSize: Long = CacheStorageDefaults.MAX_SIZE,
    ttl: Long = CacheStorageDefaults.TTL,
    directoryProvider: CacheDirectoryProvider = getCacheDirectoryProvider(),
    fileSystem: FileSystem = SystemFileSystem,
    clock: () -> Long = { getTimeMillis() }
): CacheStorage = FileCacheStorage(
    fileSystem = KotlinxIoCacheFileSystem(fileSystem),
    directoryRoot = directoryProvider.cacheDirectory,
    directoryName = directory,
    maxSize = maxSize,
    ttl = ttl,
    clock = clock
)
