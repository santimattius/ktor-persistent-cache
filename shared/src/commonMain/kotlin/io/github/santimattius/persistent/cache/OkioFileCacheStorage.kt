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

/**
 * A [CacheStorage] implementation that stores cached responses on the filesystem using Okio.
 *
 * @property config The configuration for this cache storage.
 * @property fileSystem The Okio FileSystem to use for storage operations. Defaults to [FileSystem.SYSTEM].
 */
class OkioFileCacheStorage(
    private val config: OkioFileCacheConfig,
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : CacheStorage {
    private val cacheDir = config.cacheDirectoryProvider.cacheDirectory / config.fileName
    private val cacheMutex = Mutex()
    private var initialized = false

    init {
        ensureCacheDirectoryExists()
    }

    private fun ensureCacheDirectoryExists() {
        if (!initialized) {
            fileSystem.createDirectories(cacheDir)
            initialized = true
        }
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
                ensureCacheDirectoryExists()
                val cacheFile = getCacheFile(url)
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
                ensureCacheDirectoryExists()
                val cacheFile = getCacheFile(url)
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
                ensureCacheDirectoryExists()
                val cacheFile = getCacheFile(url)
                if (!fileSystem.exists(cacheFile)) return@withLock emptySet()

                val cacheEntry = fileSystem.read(cacheFile) {
                    CacheEntry.fromByteArray(readByteArray())
                }

                // Check if the cache entry is expired
                if (isExpired(cacheEntry.timestamp)) {
                    fileSystem.delete(cacheFile)
                    return@withLock emptySet()
                }

                setOf(cacheEntry.response.restore())
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
                val cacheFile = getCacheFile(url)
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
     * Removes all cached responses for a URL.
     *
     * @param url The URL of the request
     * @throws CacheStorageException if removal fails
     */
    override suspend fun removeAll(url: Url) {
        cacheMutex.withLock {
            try {
                val cacheFile = getCacheFile(url)
                if (fileSystem.exists(cacheFile)) {
                    fileSystem.delete(cacheFile)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (e: Exception) {
                // Log error or handle it appropriately
                throw CacheStorageException("Failed to remove cache entries", e)
            }
        }
    }

    private fun getCacheFile(url: Url): Path {
        val cacheKey = Base64.encode(url.toString().encodeToByteArray())
        return cacheDir / "$cacheKey.cache"
    }

    private fun isExpired(timestamp: Long): Boolean {
        val currentTime = getTimeMillis()
        val elapsed = currentTime - timestamp
        return elapsed > config.ttl
    }

    /**
     * Internal cleanup method - MUST be called from within mutex lock.
     * Removes expired entries and enforces size limits using LRU eviction.
     */
    private fun cleanupCacheInternal() {
        if (config.maxSize <= 0) return

        try {
            var totalSize = 0L
            val files = fileSystem.list(cacheDir)
                .filter { it.name.endsWith(".cache") }
                .map { file ->
                    val attributes = fileSystem.metadata(file)
                    val size = attributes.size ?: return@map null
                    val lastModifiedAtMillis =
                        attributes.lastModifiedAtMillis ?: return@map null
                    CacheFileInfo(file, size, lastModifiedAtMillis)
                }.filterNotNull()
                .sortedByDescending { it.lastModified }

            // Calculate total size and find files to delete if over limit
            val filesToKeep = mutableListOf<CacheFileInfo>()
            for (file in files) {
                if (totalSize + file.size <= config.maxSize) {
                    totalSize += file.size
                    filesToKeep.add(file)
                } else {
                    fileSystem.delete(file.path)
                }
            }

            // Remove expired files from the ones we're keeping
            val currentTime = getTimeMillis()
            filesToKeep.forEach { file ->
                if (currentTime - file.lastModified > config.ttl) {
                    fileSystem.delete(file.path)
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
    val lastModified: Long
)

/**
 * Exception thrown when an error occurs in the cache storage.
 */
class CacheStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
