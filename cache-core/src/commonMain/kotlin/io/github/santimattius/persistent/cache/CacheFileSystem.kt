package io.github.santimattius.persistent.cache

/**
 * Filesystem metadata needed by the caching algorithm: entry size (for LRU eviction) and
 * last-modified time (as an LRU tie-breaker when the stored [CacheEntry][io.github.santimattius.persistent.cache]
 * timestamp has coarse resolution).
 *
 * @property size File size in bytes, or `null` if unknown.
 * @property lastModifiedAtMillis Last-modified time in epoch millis, or `null` if unknown.
 */
@InternalPersistentCacheApi
public data class CacheFileMetadata(
    val size: Long?,
    val lastModifiedAtMillis: Long?
)

/**
 * Backend SPI for [FileCacheStorage]: every I/O operation the caching algorithm needs,
 * expressed over an opaque path type [P] so `cache-core` never depends on a concrete
 * filesystem library (Okio, kotlinx-io, or an in-memory test double).
 *
 * Implementations are expected to be safe to call concurrently only insofar as the caller
 * (`FileCacheStorage`) already serializes access with its own mutex; a [CacheFileSystem] does
 * not need its own internal locking.
 */
@InternalPersistentCacheApi
public interface CacheFileSystem<P> {

    /**
     * Resolves [base] (a plain directory path, e.g. from `CacheDirectoryProvider`) joined with
     * [segments] in order, into this backend's path type.
     */
    public fun resolve(base: String, vararg segments: String): P

    /**
     * Returns the final path segment (file or directory name) of [path].
     */
    public fun name(path: P): String

    /**
     * Creates [dir] and any missing parent directories. Must be idempotent: calling it again on
     * an already-existing directory is not an error.
     */
    public fun createDirectories(dir: P)

    /**
     * Returns whether [path] exists (as a file or a directory).
     */
    public fun exists(path: P): Boolean

    /**
     * Reads the full contents of the file at [path].
     *
     * @throws Exception if [path] does not exist or cannot be read.
     */
    public fun read(path: P): ByteArray

    /**
     * Writes [bytes] to the file at [path], creating or overwriting it.
     */
    public fun write(path: P, bytes: ByteArray)

    /**
     * Lists the direct children of directory [dir]. Returns an empty list if [dir] does not
     * exist or has no children.
     */
    public fun list(dir: P): List<P>

    /**
     * Deletes the file or directory at [path]. Must not throw if [path] does not exist.
     */
    public fun delete(path: P)

    /**
     * Returns metadata for the file at [path], or `null` if it does not exist.
     */
    public fun metadata(path: P): CacheFileMetadata?
}
