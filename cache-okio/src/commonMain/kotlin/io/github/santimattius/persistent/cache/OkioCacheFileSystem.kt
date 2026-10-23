package io.github.santimattius.persistent.cache

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

/** Suffix of the temporary sibling file used by atomic writes. Never ends in `.cache`. */
private const val TMP_SUFFIX = ".tmp"

/**
 * [CacheFileSystem]<[Path]> adapter over a real Okio [FileSystem]. This is the first concrete
 * backend for `cache-core`'s [FileCacheStorage]: every I/O call `cache-core` needs is delegated
 * here, unchanged in on-disk behavior from the pre-split `OkioFileCacheStorage`.
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

    /**
     * Writes [bytes] to a `<name>.tmp` sibling and moves it onto [path].
     *
     * Verified against Okio 3.18.2 sources: `FileSystem.atomicMove` documents that an existing
     * target is replaced (it throws only "if the target already exists and cannot be replaced").
     * - JVM and Android API 26+: `FileSystem.SYSTEM` is `NioSystemFileSystem`, whose
     *   `atomicMove` is `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)`.
     * - Android API 24-25: no `java.nio.file.Files`, so `SYSTEM` is `JvmSystemFileSystem`, which
     *   uses `File.renameTo`; this replaces on POSIX (its source notes it fails on Windows, which
     *   always has NIO).
     * - iOS/macOS/Linux: `PosixFileSystem.variantMove` is `rename(2)`, which replaces atomically.
     */
    @OptIn(InternalPersistentCacheApi::class)
    override fun writeAtomically(path: Path, bytes: ByteArray) {
        val tmp = path.parent?.resolve(path.name + TMP_SUFFIX) ?: (path.name + TMP_SUFFIX).toPath()
        try {
            fileSystem.write(tmp) { write(bytes) }
            fileSystem.atomicMove(tmp, path)
        } catch (e: Throwable) {
            try {
                fileSystem.delete(tmp, mustExist = false)
            } catch (_: Exception) {
            }
            throw e
        }
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
