package io.github.santimattius.persistent.cache

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * [CacheFileSystem]<[Path]> adapter over kotlinx-io's [SystemFileSystem]. This is the second
 * concrete backend for `cache-core`'s [FileCacheStorage] (design decision #3, Engram #1505),
 * gated behind [ExperimentalKotlinxIoCache] because kotlinx-io's own `kotlinx.io.files` package is
 * Alpha-stability upstream (kotlinx-io 0.9.1) — Okio remains the recommended default backend.
 *
 * @property fileSystem The backing kotlinx-io [FileSystem]. Defaults to [SystemFileSystem]; tests
 *   use a real, unique temp directory per test since kotlinx-io ships no in-memory fake
 *   filesystem equivalent to Okio's `okio-fakefilesystem` in this version.
 */
@ExperimentalKotlinxIoCache
@InternalPersistentCacheApi
public class KotlinxIoCacheFileSystem(
    private val fileSystem: FileSystem = SystemFileSystem
) : CacheFileSystem<Path> {

    @OptIn(InternalPersistentCacheApi::class)
    override fun resolve(base: String, vararg segments: String): Path = Path(base, *segments)

    @OptIn(InternalPersistentCacheApi::class)
    override fun name(path: Path): String = path.name

    @OptIn(InternalPersistentCacheApi::class)
    override fun createDirectories(dir: Path) {
        fileSystem.createDirectories(dir)
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun exists(path: Path): Boolean = fileSystem.exists(path)

    @OptIn(InternalPersistentCacheApi::class)
    override fun read(path: Path): ByteArray =
        fileSystem.source(path).buffered().use { it.readByteArray() }

    @OptIn(InternalPersistentCacheApi::class)
    override fun write(path: Path, bytes: ByteArray) {
        fileSystem.sink(path).buffered().use { it.write(bytes) }
    }

    // Matches OkioCacheFileSystem.list()'s `listOrNull()` semantics: a missing directory yields
    // an empty list, but any other failure (e.g. a permission error) propagates instead of being
    // silently swallowed.
    @OptIn(InternalPersistentCacheApi::class)
    override fun list(dir: Path): List<Path> {
        if (!fileSystem.exists(dir)) return emptyList()
        return fileSystem.list(dir).toList()
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun delete(path: Path) {
        fileSystem.delete(path, mustExist = false)
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun metadata(path: Path): CacheFileMetadata? {
        val metadata = fileSystem.metadataOrNull(path) ?: return null
        return CacheFileMetadata(
            size = metadata.size,
            // kotlinx-io's FileMetadata (0.9.1) has no last-modified-time field at all, unlike
            // Okio's Metadata.lastModifiedAtMillis — see design deviation notes. FileCacheStorage
            // only uses this as an LRU tie-breaker when the stored CacheEntry timestamp is equal
            // between two files, and safely defaults to 0L when null (see FileCacheStorage's
            // `CacheFileInfo` construction), so cleanup correctness is unaffected: the primary
            // sort key (each entry's own stored timestamp) still drives eviction order.
            lastModifiedAtMillis = null
        )
    }
}
