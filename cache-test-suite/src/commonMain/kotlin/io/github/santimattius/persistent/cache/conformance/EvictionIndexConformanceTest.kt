package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.CountingCacheFileSystem
import io.github.santimattius.persistent.cache.conformance.support.ReversedListingCacheFileSystem
import io.github.santimattius.persistent.cache.conformance.support.TestClock
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for size-based eviction driven by the in-memory index: real LRU order and no
 * disk reads once the index is built.
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class EvictionIndexConformanceTest<P> : CacheConformanceSupport<P>() {

    private val clock = TestClock(1_000)

    private fun urlOf(name: String) = Url("https://api.example.com/$name")

    private suspend fun io.github.santimattius.persistent.cache.FileCacheStorage<P>.put(name: String) {
        clock.advance(10)
        val url = urlOf(name)
        // Fixed-length bodies keep every entry the same size.
        store(url, TestDataFactory.createCachedResponse(url.toString(), body = "body-$name"))
    }

    /** Size of one entry, measured by storing it in an unlimited storage and deleting it again. */
    private suspend fun entrySize(): Long {
        val probe = newStorage(directoryName = "probe", maxSize = 0, clock = clock::now)
        probe.put("a")
        return fileSystem.read(cacheFiles("probe").single()).size.toLong()
    }

    private fun cachedNames(): Set<String> = cacheFiles().map { fileSystem.name(it) }.toSet()

    private suspend fun survivors(storage: io.github.santimattius.persistent.cache.FileCacheStorage<P>, vararg names: String) =
        names.filter { storage.find(urlOf(it), emptyMap()) != null }.toSet()

    @Test
    fun `given an index built when stores trigger eviction then cleanup performs no reads`() = runTest {
        // Given - room for two entries; the first store builds the index
        val size = entrySize()
        val counting = CountingCacheFileSystem(fileSystem)
        val storage = newStorage(maxSize = size * 2 + size / 2, clock = clock::now, backend = counting)
        storage.put("a")
        counting.resetReads()

        // When - several stores, each evicting
        storage.put("b")
        storage.put("c")
        storage.put("d")

        // Then
        assertEquals(0, counting.reads, "cleanup must not read entries once the index exists")
        assertEquals(2, cacheFiles().size)
    }

    @Test
    fun `given a recently read entry when eviction runs then it survives over an older untouched one`() =
        runTest {
            // Given - room for two entries
            val size = entrySize()
            val storage = newStorage(maxSize = size * 2 + size / 2, clock = clock::now)
            storage.put("a")
            storage.put("b")

            // When - "a" is read, then a third entry forces an eviction
            clock.advance(10)
            storage.find(urlOf("a"), emptyMap())
            storage.put("c")

            // Then - "b" (least recently used) is evicted, not "a" (oldest written)
            assertEquals(setOf("a", "c"), survivors(storage, "a", "b", "c"))
        }

    @Test
    fun `given entries on disk when a new storage evicts then the oldest stored entry goes first`() = runTest {
        // Given - two entries written by a previous instance
        val size = entrySize()
        newStorage(maxSize = 0, clock = clock::now).apply {
            put("a")
            put("b")
        }

        // When - a new instance builds its index from disk and stores a third entry
        val restarted = newStorage(maxSize = size * 2 + size / 2, clock = clock::now)
        restarted.put("c")

        // Then
        assertEquals(setOf("b", "c"), survivors(restarted, "a", "b", "c"))
    }

    @Test
    fun `given removed entries when eviction runs then their size no longer counts`() = runTest {
        // Given
        val size = entrySize()
        val storage = newStorage(maxSize = size * 2 + size / 2, clock = clock::now)
        storage.put("a")
        storage.put("b")
        storage.remove(urlOf("a"), emptyMap())

        // When
        storage.put("c")

        // Then - had "a" stayed in the index, "b" would have been evicted
        assertEquals(setOf("b", "c"), survivors(storage, "a", "b", "c"))
        assertEquals(2, cachedNames().size)
    }

    @Test
    fun `given a cleared cache when new entries are stored then nothing is evicted by stale index data`() =
        runTest {
            // Given
            val size = entrySize()
            val storage = newStorage(maxSize = size * 2 + size / 2, clock = clock::now)
            storage.put("a")
            storage.put("b")
            storage.clear()

            // When
            storage.put("c")
            storage.put("d")

            // Then
            assertEquals(setOf("c", "d"), survivors(storage, "c", "d"))
        }

    @Test
    fun `given entries with equal timestamps when a new storage evicts then the result does not depend on scan order`() =
        runTest {
            // Given - identical entries written at one instant into two directories
            val fixed = { 1_000L }
            val names = listOf("a", "b", "c")
            for (dir in listOf("forward", "reversed")) {
                val writer = newStorage(directoryName = dir, maxSize = 0, clock = fixed)
                names.forEach {
                    writer.store(urlOf(it), TestDataFactory.createCachedResponse(urlOf(it).toString(), body = "body-$it"))
                }
            }
            val size = fileSystem.read(cacheFiles("forward").first()).size.toLong()
            val newcomer = urlOf("d")

            // When - restarts that scan the directory in opposite orders each evict down to 2 entries
            val forward = newStorage(directoryName = "forward", maxSize = size * 2 + size / 2, clock = fixed)
            val reversed = newStorage(
                directoryName = "reversed",
                maxSize = size * 2 + size / 2,
                clock = fixed,
                backend = ReversedListingCacheFileSystem(fileSystem)
            )
            for (storage in listOf(forward, reversed)) {
                storage.store(newcomer, TestDataFactory.createCachedResponse(newcomer.toString(), body = "body-d"))
            }

            // Then - ties break by file name, so both evict the same (smallest-named) entries
            val survivingForward = cacheFiles("forward").map { fileSystem.name(it) }.toSet()
            val survivingReversed = cacheFiles("reversed").map { fileSystem.name(it) }.toSet()
            assertEquals(survivingForward, survivingReversed)
        }
}
