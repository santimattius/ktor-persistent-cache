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
import kotlinx.serialization.protobuf.ProtoBuf
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A [CacheStorage] implementation that stores cached responses on the filesystem using Okio.
 *
 * @property config The configuration for this cache storage.
 * @property fileSystem The Okio FileSystem to use for storage operations. Defaults to [FileSystem.SYSTEM].
 */
internal class OkioFileCacheStorage(
    private val config: OkioFileCacheConfig,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : CacheStorage {
    private val cacheDir = config.cacheDirectoryProvider.cacheDirectory / config.fileName
    private val cacheMutex = Mutex()

    init {
        // Create cache directory once during initialization
        // This is thread-safe as construction happens before the instance is shared
        fileSystem.createDirectories(cacheDir)
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
                    timestamp = getTimeMillis()
                )
                fileSystem.write(cacheFile) {
                    write(cacheEntry.toByteArray())
                }
                cleanupCacheInternal()
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                // Log error or handle it appropriately
                throw CacheStorageException("Failed to store cache entry", e)
            }
        }
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        return cacheMutex.withLock {
            try {
                val cacheFile = getCacheFile(url, varyKeys)
                if (!fileSystem.exists(cacheFile)) return@withLock null

                val cacheEntry = fileSystem.read(cacheFile) {
                    CacheEntry.fromByteArray(readByteArray())
                }

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

    override suspend fun findAll(url: Url): Set<CachedResponseData> {
        return cacheMutex.withLock {
            try {
                val urlPrefix = getUrlCacheKeyPrefix(url)
                val matchingFiles = fileSystem.list(cacheDir)
                    .filter { it.name.startsWith(urlPrefix) && it.name.endsWith(".cache") }

                if (matchingFiles.isEmpty()) return@withLock emptySet()

                val results = mutableSetOf<CachedResponseData>()
                for (file in matchingFiles) {
                    try {
                        val cacheEntry = fileSystem.read(file) {
                            CacheEntry.fromByteArray(readByteArray())
                        }

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
                    .filter { it.name.startsWith(urlPrefix) && it.name.endsWith(".cache") }

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
     * Generates the cache file path for a URL with optional vary keys.
     * Uses URL-safe Base64 encoding to avoid filesystem issues with special characters.
     *
     * File naming scheme: {urlKey}_{varyKeysHash}.cache
     * - urlKey: Base64 encoded URL
     * - varyKeysHash: Hash of sorted vary keys (or "0" if empty)
     *
     * This scheme allows prefix matching for findAll/removeAll operations.
     *
     * @param url The request URL
     * @param varyKeys Optional vary keys for content negotiation differentiation
     * @return The cache file path
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun getCacheFile(url: Url, varyKeys: Map<String, String> = emptyMap()): Path {
        val urlKey = Base64.UrlSafe.encode(url.toString().encodeToByteArray())
        val varyKeysHash = if (varyKeys.isEmpty()) {
            "0"
        } else {
            val varyKeysString = varyKeys.entries
                .sortedBy { it.key }
                .joinToString(";") { "${it.key}=${it.value}" }
            varyKeysString.hashCode().toUInt().toString(16)
        }
        return cacheDir / "${urlKey}_${varyKeysHash}.cache"
    }

    /**
     * Gets the URL-only cache key prefix for matching all entries of a URL.
     * Used by findAll() and removeAll() to find entries regardless of vary keys.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun getUrlCacheKeyPrefix(url: Url): String {
        return Base64.UrlSafe.encode(url.toString().encodeToByteArray()) + "_"
    }

    private fun isExpired(timestamp: Long): Boolean {
        val currentTime = getTimeMillis()
        val elapsed = currentTime - timestamp
        return elapsed > config.ttl
    }

    /**
     * Internal cleanup method - MUST be called from within mutex lock.
     * Removes expired entries and enforces size limits using LRU eviction.
     *
     * Uses stored timestamp from CacheEntry for consistent TTL checking
     * (same as find() and findAll() methods).
     */
    private fun cleanupCacheInternal() {
        if (config.maxSize <= 0) return

        try {
            // Collect cache file info including stored timestamp from CacheEntry
            val cacheFiles = fileSystem.list(cacheDir)
                .filter { it.name.endsWith(".cache") }
                .mapNotNull { file ->
                    try {
                        val size = fileSystem.metadata(file).size ?: return@mapNotNull null
                        val cacheEntry = fileSystem.read(file) {
                            CacheEntry.fromByteArray(readByteArray())
                        }
                        CacheFileInfo(file, size, cacheEntry.timestamp)
                    } catch (_: Exception) {
                        // Delete corrupted files
                        try { fileSystem.delete(file) } catch (_: Exception) { }
                        null
                    }
                }
                .sortedByDescending { it.timestamp } // Sort by stored timestamp (newest first)

            var totalSize = 0L

            for (fileInfo in cacheFiles) {
                // First check: remove expired entries
                if (isExpired(fileInfo.timestamp)) {
                    fileSystem.delete(fileInfo.path)
                    continue
                }

                // Second check: enforce size limit (LRU eviction)
                if (totalSize + fileInfo.size <= config.maxSize) {
                    totalSize += fileInfo.size
                } else {
                    fileSystem.delete(fileInfo.path)
                }
            }
        } catch (e: Exception) {
            // Log error or handle it appropriately
            println("Failed to cleanup cache: ${e.message}")
        }
    }
}

@Serializable
private data class CacheEntry(
    val url: String,
    @Contextual val response: CachedResponseDataCopy,
    val timestamp: Long
) {
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromByteArray(bytes: ByteArray): CacheEntry {
            // Implement deserialization from ByteArray to CacheEntry
            // This is a simplified version - you might want to use a proper serialization library
            return ProtoBuf.decodeFromByteArray(serializer(), bytes)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun toByteArray(): ByteArray {
        // Implement serialization from CacheEntry to ByteArray
        // This is a simplified version - you might want to use a proper serialization library
        return ProtoBuf.encodeToByteArray(serializer(), this)
    }
}

private data class CacheFileInfo(
    val path: Path,
    val size: Long,
    val timestamp: Long // Stored timestamp from CacheEntry for consistent TTL checking
)

/**
 * Exception thrown when an error occurs in the cache storage.
 */
class CacheStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
