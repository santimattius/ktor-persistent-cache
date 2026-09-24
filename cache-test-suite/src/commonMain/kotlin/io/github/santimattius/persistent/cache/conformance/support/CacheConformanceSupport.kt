package io.github.santimattius.persistent.cache.conformance.support

import io.github.santimattius.persistent.cache.CacheFileSystem
import io.github.santimattius.persistent.cache.FileCacheStorage
import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.ktor.util.date.getTimeMillis
import kotlin.test.BeforeTest

/**
 * Shared setup/helpers for every conformance suite in this module.
 *
 * A concrete backend (an in-memory reference double, `cache-okio`, `cache-kotlinx-io`)
 * subclasses each conformance test with a one-line [createFileSystem] factory, per design
 * decision #5 (Engram #1505): the same test bodies run unmodified against every backend.
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class CacheConformanceSupport<P> {

    /** Creates a fresh, empty backend filesystem for one test. */
    protected abstract fun createFileSystem(): CacheFileSystem<P>

    /** Root cache directory used by every test in this suite. */
    protected open val root: String = "/fake/cache"

    protected lateinit var fileSystem: CacheFileSystem<P>

    @BeforeTest
    fun setUpConformanceFileSystem() {
        fileSystem = createFileSystem()
    }

    /** Builds a [FileCacheStorage] backed by [fileSystem], rooted at [root]. */
    protected fun newStorage(
        directoryName: String = "http_cache",
        maxSize: Long = 10L * 1024 * 1024,
        ttl: Long = 60 * 60 * 1000,
        clock: () -> Long = { getTimeMillis() }
    ): FileCacheStorage<P> = FileCacheStorage(
        fileSystem = fileSystem,
        directoryRoot = root,
        directoryName = directoryName,
        maxSize = maxSize,
        ttl = ttl,
        clock = clock
    )

    /** The cache subdirectory path, matching what [newStorage] resolves internally. */
    protected fun cacheDir(directoryName: String = "http_cache"): P =
        fileSystem.resolve(root, directoryName)

    /** A path for a specific file under the cache subdirectory, e.g. for corrupting it in a test. */
    protected fun cacheFilePath(fileName: String, directoryName: String = "http_cache"): P =
        fileSystem.resolve(root, directoryName, fileName)

    /** All `*.cache` entries directly under the cache subdirectory. */
    protected fun cacheFiles(directoryName: String = "http_cache"): List<P> =
        fileSystem.list(cacheDir(directoryName)).filter { fileSystem.name(it).endsWith(".cache") }
}

/** A controllable clock for deterministic testing of TTL-based logic. */
class TestClock(initialTime: Long = 0L) {
    private var currentTime = initialTime

    fun now(): Long = currentTime

    fun advance(milliseconds: Long) {
        currentTime += milliseconds
    }
}
