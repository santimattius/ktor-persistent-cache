package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.Url
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.coroutines.cancellation.CancellationException

/** Suffix of the temporary sibling file used by atomic writes. Never ends in `.cache`. */
private const val TMP_SUFFIX = ".tmp"

/** Number of hex characters of the vary-keys SHA-256 used in an entry file name. */
private const val VARY_HASH_LENGTH = 16

/** Well-formed entry file name: `<64 hex url hash>_<"0" | 16 hex vary hash>.cache`. */
private val ENTRY_FILE_NAME = Regex("[0-9a-f]{64}_(0|[0-9a-f]{16})\\.cache")

/**
 * Fixed header of every entry file: the magic bytes `KPC` followed by the format version. A file
 * without it, or with another version, is treated as corrupt and deleted.
 */
private val FORMAT_HEADER = byteArrayOf('K'.code.toByte(), 'P'.code.toByte(), 'C'.code.toByte(), 1)

/**
 * A [CacheStorage] implementation that stores cached responses on the filesystem, generic over
 * the backend path type [P]. This is the algorithm previously in `OkioFileCacheStorage`
 * (mutex, key hashing, ProtoBuf [CacheEntry], TTL, LRU cleanup) with every I/O call delegated to
 * [fileSystem], so it has no dependency on any concrete filesystem library.
 *
 * @property fileSystem The backend SPI used for all filesystem operations.
 * @property directoryRoot The root cache directory (typically from a platform-specific
 *   `CacheDirectoryProvider`).
 * @property directoryName The subdirectory name under [directoryRoot] used for this storage's
 *   cache files.
 * @property maxSize The maximum size of the cache in bytes. A value `<= 0` means unlimited.
 * @property ttl The time-to-live for cache entries in milliseconds. A value `<= 0` means entries
 *   never expire.
 * @property clock Supplies the current time in milliseconds. Defaults to [getTimeMillis].
 */
@InternalPersistentCacheApi
public class FileCacheStorage<P>(
    private val fileSystem: CacheFileSystem<P>,
    private val directoryRoot: String,
    private val directoryName: String,
    private val maxSize: Long,
    private val ttl: Long,
    private val clock: () -> Long = { getTimeMillis() }
) : CacheStorage {

    private val cacheDir: P = fileSystem.resolve(directoryRoot, directoryName)
    private val cacheMutex = Mutex()

    init {
        // Create cache directory once during initialization.
        // This is thread-safe as construction happens before the instance is shared.
        fileSystem.createDirectories(cacheDir)
        deleteStaleTempFiles()
        deleteLegacyEntries()
    }

    /**
     * Stores a cached response.
     *
     * @param url The URL of the request
     * @param data The cached response data to store
     * @throws CacheStorageException if storing fails
     */
    override suspend fun store(url: Url, data: CachedResponseData) {
        cacheMutex.withLock {
            try {
                val cacheFile = getCacheFile(url, data.varyKeys)
                val cacheEntry = CacheEntry(
                    url = url.toString(),
                    response = data.makeCopy(),
                    timestamp = clock()
                )
                fileSystem.writeAtomically(cacheFile, cacheEntry.toBytes())
                cleanupCacheInternal()
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                // Log error or handle it appropriately
                throw CacheStorageException("Failed to store cache entry", e)
            }
        }
    }

    /**
     * Looks up a cached response for the given [url] and [varyKeys].
     * Returns null if not found, expired, or on read error.
     *
     * @param url The request URL.
     * @param varyKeys Vary keys for content negotiation.
     * @return The cached response, or null.
     */
    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        return cacheMutex.withLock {
            try {
                val cacheFile = getCacheFile(url, varyKeys)
                if (!fileSystem.exists(cacheFile)) return@withLock null

                val cacheEntry = readEntry(cacheFile) ?: return@withLock null

                // Defensive check: never serve an entry stored for a different vary set.
                if (cacheEntry.response.varyKeys != varyKeys) return@withLock null

                // Check if the cache entry is expired
                if (isExpired(cacheEntry.timestamp)) {
                    fileSystem.delete(cacheFile)
                    return@withLock null
                }

                cacheEntry.response.restore()
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                // Log error or handle it appropriately
                null
            }
        }
    }

    /**
     * Returns all cached responses for the given [url], for any vary keys.
     * Expired or corrupted entries are skipped (and removed when expired).
     *
     * @param url The request URL.
     * @return Set of all matching cached responses.
     */
    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return cacheMutex.withLock {
            try {
                val urlPrefix = getUrlCacheKeyPrefix(url)
                val matchingFiles = fileSystem.list(cacheDir)
                    .filter { fileSystem.name(it).startsWith(urlPrefix) && fileSystem.name(it).endsWith(".cache") }

                if (matchingFiles.isEmpty()) return@withLock emptySet()

                val results = mutableSetOf<CachedResponseData>()
                for (file in matchingFiles) {
                    try {
                        val cacheEntry = readEntry(file) ?: continue

                        // Defensive check: the file name must be the one derived from the stored vary keys.
                        if (fileSystem.name(file) != cacheFileName(url, cacheEntry.response.varyKeys)) continue

                        // Check if the cache entry is expired
                        if (isExpired(cacheEntry.timestamp)) {
                            fileSystem.delete(file)
                        } else {
                            results.add(cacheEntry.response.restore())
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        // Skip corrupted entries
                    }
                }
                results
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                // Log error or handle it appropriately
                emptySet()
            }
        }
    }

    /**
     * Removes a cached response.
     *
     * @param url The URL of the request
     * @param varyKeys The vary keys for cache lookup
     * @throws CacheStorageException if removal fails
     */
    override suspend fun remove(url: Url, varyKeys: Map<String, String>) {
        cacheMutex.withLock {
            try {
                val cacheFile = getCacheFile(url, varyKeys)
                if (fileSystem.exists(cacheFile)) {
                    fileSystem.delete(cacheFile)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                // Log error or handle it appropriately
                throw CacheStorageException("Failed to remove cache entry", e)
            }
        }
    }

    /**
     * Removes all cached responses for a URL (regardless of vary keys).
     *
     * @param url The URL of the request
     * @throws CacheStorageException if removal fails
     */
    override suspend fun removeAll(url: Url) {
        cacheMutex.withLock {
            try {
                val urlPrefix = getUrlCacheKeyPrefix(url)
                val matchingFiles = fileSystem.list(cacheDir)
                    .filter { fileSystem.name(it).startsWith(urlPrefix) && fileSystem.name(it).endsWith(".cache") }

                for (file in matchingFiles) {
                    fileSystem.delete(file)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                // Log error or handle it appropriately
                throw CacheStorageException("Failed to remove cache entries", e)
            }
        }
    }

    /**
     * Removes every cached response managed by this storage.
     *
     * @throws CacheStorageException if clearing fails
     */
    override suspend fun clear() {
        cacheMutex.withLock {
            try {
                val cacheFiles = fileSystem.list(cacheDir)
                    .filter { fileSystem.name(it).endsWith(".cache") || fileSystem.name(it).endsWith(TMP_SUFFIX) }

                for (file in cacheFiles) {
                    fileSystem.delete(file)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                throw CacheStorageException("Failed to clear cache entries", e)
            }
        }
    }

    /**
     * Generates the cache file path for a URL with optional vary keys.
     * Uses SHA-256 hashing to produce a fixed-length (64 hex chars) filename component,
     * avoiding filesystem limits on file name length regardless of URL length.
     *
     * File naming scheme: {urlHash}_{varyKeysHash}.cache
     * - urlHash: SHA-256 hex digest of the URL string (always 64 chars)
     * - varyKeysHash: first 16 hex chars of the SHA-256 of the sorted `k=v;k=v` vary keys string
     *   (or "0" if empty)
     *
     * This scheme allows prefix matching for findAll/removeAll operations.
     *
     * @param url The request URL
     * @param varyKeys Optional vary keys for content negotiation differentiation
     * @return The cache file path
     */
    private fun getCacheFile(url: Url, varyKeys: Map<String, String> = emptyMap()): P =
        fileSystem.resolve(directoryRoot, directoryName, cacheFileName(url, varyKeys))

    private fun cacheFileName(url: Url, varyKeys: Map<String, String>): String {
        val urlKey = Sha256.hex(url.toString().encodeToByteArray())
        val varyKeysHash = if (varyKeys.isEmpty()) {
            "0"
        } else {
            val varyKeysString = varyKeys.entries
                .sortedBy { it.key }
                .joinToString(";") { "${it.key}=${it.value}" }
            Sha256.hex(varyKeysString.encodeToByteArray()).take(VARY_HASH_LENGTH)
        }
        return "${urlKey}_${varyKeysHash}.cache"
    }

    /**
     * Gets the URL-only cache key prefix for matching all entries of a URL.
     * Used by findAll() and removeAll() to find entries regardless of vary keys.
     */
    private fun getUrlCacheKeyPrefix(url: Url): String {
        return Sha256.hex(url.toString().encodeToByteArray()) + "_"
    }

    /**
     * Deletes leftover `*.tmp` files from writes interrupted by a crash. They never end in
     * `.cache`, so lookups ignore them; this only reclaims their space. Best-effort.
     */
    private fun deleteStaleTempFiles() {
        try {
            fileSystem.list(cacheDir)
                .filter { fileSystem.name(it).endsWith(TMP_SUFFIX) }
                .forEach { fileSystem.delete(it) }
        } catch (_: Exception) {
            // Best-effort housekeeping; a failure must not prevent the cache from starting.
        }
    }

    /**
     * Deletes `*.cache` files whose name is not `<64 hex>_<"0" | 16 hex>.cache`. These were written
     * by versions that used a 32-bit vary suffix and can never be hit again; without this sweep they
     * would stay forever when [maxSize] `<= 0`. Costs one directory listing and no reads. Best-effort.
     */
    private fun deleteLegacyEntries() {
        try {
            fileSystem.list(cacheDir)
                .filter { path ->
                    val name = fileSystem.name(path)
                    name.endsWith(".cache") && !ENTRY_FILE_NAME.matches(name)
                }
                .forEach { fileSystem.delete(it) }
        } catch (_: Exception) {
            // Best-effort housekeeping; a failure must not prevent the cache from starting.
        }
    }

    /**
     * Reads and decodes [file]. A file with a missing or unknown header, or undecodable content, is
     * corrupt: it is deleted and `null` is returned. I/O errors propagate to the caller.
     */
    private fun readEntry(file: P): CacheEntry? {
        val bytes = fileSystem.read(file)
        val entry = CacheEntry.fromBytes(bytes)
        if (entry == null) {
            fileSystem.delete(file)
        }
        return entry
    }

    private fun isExpired(timestamp: Long): Boolean {
        if (ttl <= 0) return false // ttl <= 0 means "never expires" (mirrors maxSize <= 0 = unlimited)
        val currentTime = clock()
        val elapsed = currentTime - timestamp
        return elapsed > ttl
    }

    /**
     * Internal cleanup method - MUST be called from within mutex lock.
     * Removes expired entries and enforces size limits using LRU eviction.
     *
     * Uses stored timestamp from CacheEntry for consistent TTL checking
     * (same as find() and findAll() methods).
     */
    private fun cleanupCacheInternal() {
        if (maxSize <= 0) return

        try {
            // Collect cache file info including stored timestamp from CacheEntry and file lastModified
            val cacheFiles = fileSystem.list(cacheDir)
                .filter { fileSystem.name(it).endsWith(".cache") }
                .mapNotNull { file ->
                    try {
                        val metadata = fileSystem.metadata(file)
                        val size = metadata?.size ?: return@mapNotNull null
                        val cacheEntry = CacheEntry.fromBytes(fileSystem.read(file))
                            ?: throw IllegalStateException("Corrupt cache entry")
                        val lastModified = metadata.lastModifiedAtMillis ?: 0L
                        CacheFileInfo(file, size, cacheEntry.timestamp, lastModified)
                    } catch (_: Exception) {
                        // Delete corrupted files
                        try {
                            fileSystem.delete(file)
                        } catch (_: Exception) {
                        }
                        null
                    }
                }
                // Newest first: by stored timestamp, then by file lastModified (tie-breaker for coarse clock resolution)
                .sortedWith(compareByDescending<CacheFileInfo<P>> { it.timestamp }.thenByDescending { it.lastModifiedAtMillis })

            var totalSize = 0L

            for (fileInfo in cacheFiles) {
                // First check: remove expired entries
                if (isExpired(fileInfo.timestamp)) {
                    fileSystem.delete(fileInfo.path)
                    continue
                }

                // Second check: enforce size limit (LRU eviction)
                if (totalSize + fileInfo.size <= maxSize) {
                    totalSize += fileInfo.size
                } else {
                    fileSystem.delete(fileInfo.path)
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup; failures are ignored to avoid breaking cache reads/writes.
        }
    }
}

/**
 * On-disk representation of a single cache entry (URL, serialized response, timestamp).
 */
@Serializable
private data class CacheEntry(
    val url: String,
    @Contextual val response: CachedResponseDataCopy,
    val timestamp: Long
) {
    companion object {
        /**
         * Decodes a [CacheEntry] from file bytes: a [FORMAT_HEADER] followed by the ProtoBuf payload.
         *
         * @param bytes The file contents.
         * @return The decoded [CacheEntry], or `null` if the header is missing or unknown, or the
         *   payload cannot be decoded.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromBytes(bytes: ByteArray): CacheEntry? {
            if (bytes.size < FORMAT_HEADER.size) return null
            for (i in FORMAT_HEADER.indices) {
                if (bytes[i] != FORMAT_HEADER[i]) return null
            }
            return try {
                ProtoBuf.decodeFromByteArray(serializer(), bytes.copyOfRange(FORMAT_HEADER.size, bytes.size))
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Serializes this [CacheEntry] to file bytes: the [FORMAT_HEADER] followed by the ProtoBuf payload.
     *
     * @return The bytes to store.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun toBytes(): ByteArray {
        return FORMAT_HEADER + ProtoBuf.encodeToByteArray(serializer(), this)
    }
}

/**
 * Metadata for a cache file used during cleanup (path, size, stored timestamp, file lastModified).
 * [lastModifiedAtMillis] is used as a tie-breaker when [timestamp] is equal (e.g. coarse clock resolution on some platforms).
 */
private data class CacheFileInfo<P>(
    val path: P,
    val size: Long,
    val timestamp: Long, // Stored timestamp from CacheEntry for consistent TTL checking
    val lastModifiedAtMillis: Long = 0L // File last-modified for LRU tie-breaker
)

/**
 * Exception thrown when an error occurs in the cache storage.
 */
public class CacheStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
