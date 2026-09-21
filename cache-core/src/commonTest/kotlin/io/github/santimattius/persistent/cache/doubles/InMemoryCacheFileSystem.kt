package io.github.santimattius.persistent.cache.doubles

import io.github.santimattius.persistent.cache.CacheFileMetadata
import io.github.santimattius.persistent.cache.CacheFileSystem
import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.ktor.util.date.getTimeMillis

/**
 * Reference [CacheFileSystem]<[String]> implementation backed by in-memory maps.
 *
 * Replaces the old `doubles/FakeFileSystem.kt` (which extended `okio.FileSystem` and therefore
 * could not move to `cache-core`, per design decision #4, Engram #1505). Every backend
 * (`cache-okio`, `cache-kotlinx-io`) is expected to behave identically to this reference when
 * subclassing the shared conformance suites from `:cache-test-suite`.
 */
@OptIn(InternalPersistentCacheApi::class)
class InMemoryCacheFileSystem : CacheFileSystem<String> {

    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf<String>()
    private val lastModifiedAtMillis = mutableMapOf<String, Long>()

    override fun resolve(base: String, vararg segments: String): String {
        var result = base.trimEnd('/')
        for (segment in segments) {
            result = "$result/${segment.trim('/')}"
        }
        return result
    }

    override fun name(path: String): String = path.substringAfterLast('/')

    override fun createDirectories(dir: String) {
        var current = dir
        while (current.isNotEmpty()) {
            directories.add(current)
            val separatorIndex = current.lastIndexOf('/')
            if (separatorIndex <= 0) break
            current = current.substring(0, separatorIndex)
        }
    }

    override fun exists(path: String): Boolean = files.containsKey(path) || directories.contains(path)

    override fun read(path: String): ByteArray =
        files[path] ?: throw NoSuchElementException("File not found: $path")

    override fun write(path: String, bytes: ByteArray) {
        files[path] = bytes
        lastModifiedAtMillis[path] = getTimeMillis()
    }

    override fun list(dir: String): List<String> {
        val prefix = "$dir/"
        return (files.keys + directories)
            .filter { candidate -> candidate.startsWith(prefix) && !candidate.removePrefix(prefix).contains('/') }
            .distinct()
    }

    override fun delete(path: String) {
        files.remove(path)
        directories.remove(path)
        lastModifiedAtMillis.remove(path)
    }

    override fun metadata(path: String): CacheFileMetadata? {
        val bytes = files[path] ?: return null
        return CacheFileMetadata(size = bytes.size.toLong(), lastModifiedAtMillis = lastModifiedAtMillis[path])
    }

    // Test-only helpers for setup/assertions beyond the plain SPI surface.

    /** Removes all files and directories tracked by this fake, resetting it to empty. */
    fun clear() {
        files.clear()
        directories.clear()
        lastModifiedAtMillis.clear()
    }

    /** Overrides the last-modified time of [path], used to test LRU tie-breaking deterministically. */
    fun setLastModified(path: String, timestamp: Long) {
        lastModifiedAtMillis[path] = timestamp
    }
}
