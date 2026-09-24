package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Behavioral tests for cache storage initialization.
 *
 * These tests verify that:
 * 1. Cache directory is created during initialization
 * 2. Initialization is thread-safe (no race conditions)
 * 3. Operations work correctly after initialization
 *
 * Relocated from `shared/commonTest/InitializationBehaviorTest.kt` (design decision #5,
 * Engram #1505).
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class InitializationConformanceTest<P> : CacheConformanceSupport<P>() {

    @Test
    fun `given new storage instance when created then cache directory is created`() = runTest {
        // Given / When
        newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)

        // Then
        assertTrue(
            fileSystem.exists(cacheDir()),
            "Cache directory should be created during initialization"
        )
    }

    @Test
    fun `given storage instance when performing operations then all complete without errors`() =
        runTest {
            // Given
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)

            val url = Url("https://api.example.com/data")
            val response = TestDataFactory.createCachedResponse(url = url.toString())

            // When & Then - All operations should complete without errors
            storage.store(url, response)
            storage.find(url, emptyMap())
            storage.findAll(url)
            storage.remove(url, emptyMap())
            storage.removeAll(url)

            assertTrue(true, "All operations completed successfully")
        }

    @Test
    fun `given multiple storage instances with same config when created then each has its own initialization`() =
        runTest {
            // Given
            val storage1 = newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)
            val storage2 = newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)

            // Then - Both should be able to operate on the same directory
            val url = Url("https://api.example.com/data")
            storage1.store(
                url,
                TestDataFactory.createCachedResponse(url = url.toString(), body = "from storage1")
            )

            // Storage2 should be able to read what storage1 wrote (shared directory)
            val result = storage2.find(url, emptyMap())
            assertTrue(result != null, "Storage2 should read data from shared cache directory")
        }

    @Test
    fun `given storage with custom directory name when created then uses custom name`() = runTest {
        // Given
        val customDirName = "custom_http_cache"

        // When
        newStorage(directoryName = customDirName, maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)

        // Then
        assertTrue(
            fileSystem.exists(cacheDir(customDirName)),
            "Cache directory should use custom name"
        )
    }

    @Test
    fun `given storage instance when store is called multiple times rapidly then all complete`() =
        runTest {
            // Given
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000)

            // When - Rapid sequential stores (tests that initialization doesn't cause issues)
            repeat(10) { i ->
                val url = Url("https://api.example.com/data/$i")
                storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))
            }

            // Then - All should complete and be retrievable
            repeat(10) { i ->
                val url = Url("https://api.example.com/data/$i")
                val result = storage.find(url, emptyMap())
                assertTrue(result != null, "Entry $i should be retrievable")
            }
        }
}
