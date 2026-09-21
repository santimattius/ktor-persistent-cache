package io.github.santimattius.persistent.cache

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * [CacheFileSystem]<[Path]> adapter over a real Okio [FileSystem]. This is the first concrete
 * backend for `cache-core`'s [FileCacheStorage]: every I/O call `cache-core` needs is delegated
 * here, unchanged in on-disk behavior from the pre-split `OkioFileCacheStorage` (design decision
 * #6, Engram #1505).
 *
 * @property fileSystem The backing Okio [FileSystem]. Defaults to [FileSystem.SYSTEM]; tests
 *   typically pass an `okio.fakefilesystem.FakeFileSystem` instead.
 */
@InternalPersistentCacheApi
public class OkioCacheFileSystem(
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) : CacheFileSystem<Path> {

    @OptIn(InternalPersistentCacheApi::class)
    override fun resolve(base: String, vararg segments: String): Path =
        segments.fold(base.toPath()) { acc, segment -> acc / segment }

    @OptIn(InternalPersistentCacheApi::class)
    override fun name(path: Path): String = path.name

    @OptIn(InternalPersistentCacheApi::class)
    override fun createDirectories(dir: Path) {
        fileSystem.createDirectories(dir)
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun exists(path: Path): Boolean = fileSystem.exists(path)

    @OptIn(InternalPersistentCacheApi::class)
    override fun read(path: Path): ByteArray = fileSystem.read(path) { readByteArray() }

    @OptIn(InternalPersistentCacheApi::class)
    override fun write(path: Path, bytes: ByteArray) {
        fileSystem.write(path) { write(bytes) }
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun list(dir: Path): List<Path> = fileSystem.listOrNull(dir) ?: emptyList()

    @OptIn(InternalPersistentCacheApi::class)
    override fun delete(path: Path) {
        fileSystem.delete(path, mustExist = false)
    }

    @OptIn(InternalPersistentCacheApi::class)
    override fun metadata(path: Path): CacheFileMetadata? {
        val metadata = fileSystem.metadataOrNull(path) ?: return null
        return CacheFileMetadata(
            size = metadata.size,
            lastModifiedAtMillis = metadata.lastModifiedAtMillis
        )
    }
}
