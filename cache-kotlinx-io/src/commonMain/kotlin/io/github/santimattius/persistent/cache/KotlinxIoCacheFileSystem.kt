package io.github.santimattius.persistent.cache

import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** Suffix of the temporary sibling file used by atomic writes. Never ends in `.cache`. */
private const val TMP_SUFFIX = ".tmp"

/**
 * [CacheFileSystem]<[Path]> adapter over kotlinx-io's [SystemFileSystem]. This is the second
 * concrete backend for `cache-core`'s [FileCacheStorage],
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

    /**
     * Writes [bytes] to a `<name>.tmp` sibling and moves it onto [path].
     *
     * Verified against kotlinx-io 0.9.1 sources (`FileSystem.atomicMove` is documented as
     * "overriding [destination] if it already exists"):
     * - JVM and Android API 26+: `FileSystemJvm.kt` `NioMover` calls
     *   `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)`, which replaces an existing target
     *   atomically (POSIX `rename(2)`; `MoveFileEx` with `MOVEFILE_REPLACE_EXISTING` on Windows).
     * - iOS/macOS (`FileSystemApple.kt`) and Linux (`FileSystemUnix.kt`): `atomicMoveImpl` is
     *   `rename(2)`, which atomically replaces an existing regular file.
     * - Android API 24-25 (this library's minSdk is 24): `java.nio.file.Files` does not exist, so
     *   the JVM `mover` throws [UnsupportedOperationException]. That case is handled below by a
     *   non-atomic direct write.
     * - Windows native: not verified; this project builds no such target.
     *
     * Defensive behaviour for backends that refuse to replace: if the move fails with an
     * [IOException], the destination is deleted and the move is retried once. The temp file is
     * always cleaned up.
     */
    @OptIn(InternalPersistentCacheApi::class)
    override fun writeAtomically(path: Path, bytes: ByteArray) {
        writeAtomically(path, bytes, fileSystem::atomicMove)
    }

    internal fun writeAtomically(path: Path, bytes: ByteArray, move: (Path, Path) -> Unit) {
        val parent = path.parent
        val tmpName = path.name + TMP_SUFFIX
        val tmp = if (parent != null) Path(parent, tmpName) else Path(tmpName)
        try {
            write(tmp, bytes)
            try {
                move(tmp, path)
            } catch (_: UnsupportedOperationException) {
                // No atomic move on this platform (Android API 24-25): degrade to a direct write.
                write(path, bytes)
            } catch (_: IOException) {
                fileSystem.delete(path, mustExist = false)
                move(tmp, path)
            }
        } finally {
            try {
                fileSystem.delete(tmp, mustExist = false)
            } catch (_: Exception) {
            }
        }
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
