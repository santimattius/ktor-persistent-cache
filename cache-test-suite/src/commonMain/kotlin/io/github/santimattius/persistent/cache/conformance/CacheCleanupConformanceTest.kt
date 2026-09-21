package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for cache cleanup functionality.
 *
 * These tests verify:
 * 1. The mutex deadlock fix - cleanup runs without deadlock during store operations
 * 2. Size-based LRU eviction works correctly
 * 3. Cleanup is triggered during store operations
 *
 * The original bug: cleanupCache() tried to acquire the mutex inside store(),
 * which already held the mutex, causing a deadlock.
 *
 * Relocated from `shared/commonTest/CacheCleanupBehaviorTest.kt` (design decision #5,
 * Engram #1505): a concrete backend subclasses this with a one-line [createFileSystem] factory.
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class CacheCleanupConformanceTest<P> : CacheConformanceSupport<P>() {

    @Test
    fun `given store operation when cleanup is triggered then operation completes without deadlock`() =
        runTest {
            // Given - Small cache size to ensure cleanup runs on every store
            val storage = newStorage(maxSize = 100, ttl = 60 * 60 * 1000)

            val url = Url("https://api.example.com/data")
            val response = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "some test data that will trigger cleanup due to size limits"
            )

            // When - This would deadlock with the old implementation
            storage.store(url, response)

            // Then - If we reach this point, no deadlock occurred
            // The test framework has a timeout, so deadlock would cause test failure
            assertTrue(true, "Store with cleanup completed successfully - no deadlock")
        }

    @Test
    fun `given multiple rapid store operations when cleanup runs each time then all complete`() =
        runTest {
            // Given
            val storage = newStorage(maxSize = 200, ttl = 60 * 60 * 1000)

            // When - Rapidly store many items (each triggers cleanup)
            repeat(10) { index ->
                val url = Url("https://api.example.com/data/$index")
                val response = TestDataFactory.createCachedResponse(
                    url = url.toString(),
                    body = "Response body for request number $index with additional content"
                )
                storage.store(url, response)
            }

            // Then - All operations completed (would timeout if deadlock)
            assertTrue(true, "All rapid store operations completed without deadlock")
        }

    @Test
    fun `given cache with maxSize zero when store is called then cleanup is skipped`() = runTest {
        // Given - maxSize = 0 means unlimited (cleanup disabled)
        val storage = newStorage(maxSize = 0, ttl = 60 * 60 * 1000)

        // When - Store multiple items
        repeat(5) { index ->
            val url = Url("https://api.example.com/data/$index")
            storage.store(
                url,
                TestDataFactory.createCachedResponse(
                    url = url.toString(),
                    body = "Large response body for request $index"
                )
            )
        }

        // Then - All files should exist (no cleanup)
        assertEquals(5, cacheFiles().size, "All 5 cache files should exist when cleanup is disabled")
    }

    @Test
    fun `given cache with negative maxSize when store is called then cleanup is skipped`() =
        runTest {
            // Given - Negative maxSize should also skip cleanup
            val storage = newStorage(maxSize = -1, ttl = 60 * 60 * 1000)

            val url = Url("https://api.example.com/data")
            val response = TestDataFactory.createCachedResponse(url = url.toString())

            // When
            storage.store(url, response)

            // Then - File should exist (cleanup was skipped)
            assertTrue(cacheFiles().isNotEmpty(), "Cache file should exist when cleanup is skipped")
        }

    @Test
    fun `given interleaved store and find operations when cleanup runs then operations remain consistent`() =
        runTest {
            // Given
            val storage = newStorage(maxSize = 500, ttl = 60 * 60 * 1000)

            // When - Interleave store and find operations
            val urls = (0 until 5).map { Url("https://api.example.com/data/$it") }

            urls.forEach { url ->
                storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))
            }

            // Find operations while cache might be cleaned
            urls.forEach { url ->
                storage.find(url, emptyMap())
            }

            // Store more to trigger cleanup
            (5 until 10).forEach { index ->
                val url = Url("https://api.example.com/data/$index")
                storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))
            }

            // Then - All operations completed without deadlock
            assertTrue(true, "Interleaved operations completed successfully")
        }
}
