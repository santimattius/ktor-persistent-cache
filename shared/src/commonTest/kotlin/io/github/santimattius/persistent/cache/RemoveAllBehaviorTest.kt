package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.github.santimattius.persistent.cache.doubles.FakeFileSystem
import io.github.santimattius.persistent.cache.doubles.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the removeAll operation.
 *
 * These tests verify that the removeAll fix works correctly:
 * - The original bug tried to create directories at the file path after deletion
 * - The fix simply deletes the file without attempting to recreate anything
 *
 * Uses Given-When-Then pattern for clarity.
 */
class RemoveAllBehaviorTest {

    private lateinit var fakeFileSystem: FakeFileSystem
    private lateinit var storage: OkioFileCacheStorage
    private lateinit var config: OkioFileCacheConfig

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        fakeFileSystem.createDirectory("/fake/cache".toOkioPath())

        config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )

        storage = OkioFileCacheStorage(config, fakeFileSystem)
    }

    @AfterTest
    fun tearDown() {
        fakeFileSystem.clear()
    }

    @Test
    fun `given a cached entry when removeAll is called then only the file is deleted`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, response)

        // Capture the cache file path
        val cacheFiles = fakeFileSystem.getAllFiles().filter {
            it.name.endsWith(".cache")
        }
        assertEquals(1, cacheFiles.size, "Should have exactly one cache file")
        val cacheFilePath = cacheFiles.first()

        // When
        storage.removeAll(url)

        // Then
        assertFalse(
            fakeFileSystem.fileExists(cacheFilePath),
            "Cache file should be deleted"
        )
        // The cache directory should still exist (not be replaced by a file or corrupted)
        assertTrue(
            fakeFileSystem.directoryExists("/fake/cache/http_cache".toOkioPath()),
            "Cache directory should still exist"
        )
    }

    @Test
    fun `given removeAll is called when cache entry does not exist then no error occurs`() =
        runTest {
            // Given
            val url = Url("https://api.example.com/nonexistent")
            // No entry stored

            // When & Then - should not throw
            storage.removeAll(url)
            assertTrue(true, "removeAll should complete without error for non-existent entry")
        }

    @Test
    fun `given multiple cache entries when removeAll is called for one then others remain intact`() =
        runTest {
            // Given
            val url1 = Url("https://api.example.com/data1")
            val url2 = Url("https://api.example.com/data2")
            val url3 = Url("https://api.example.com/data3")

            storage.store(
                url1,
                TestDataFactory.createCachedResponse(url = url1.toString(), body = "data1")
            )
            storage.store(
                url2,
                TestDataFactory.createCachedResponse(url = url2.toString(), body = "data2")
            )
            storage.store(
                url3,
                TestDataFactory.createCachedResponse(url = url3.toString(), body = "data3")
            )

            // Verify all three exist
            val filesBefore = fakeFileSystem.getAllFiles().filter { it.name.endsWith(".cache") }
            assertEquals(3, filesBefore.size, "Should have three cache files")

            // When
            storage.removeAll(url2)

            // Then
            assertNull(storage.find(url2, emptyMap()), "Removed entry should not be found")
            assertNotNull(storage.find(url1, emptyMap()), "Other entry 1 should still exist")
            assertNotNull(storage.find(url3, emptyMap()), "Other entry 3 should still exist")

            val filesAfter = fakeFileSystem.getAllFiles().filter { it.name.endsWith(".cache") }
            assertEquals(2, filesAfter.size, "Should have two cache files remaining")
        }

    @Test
    fun `given removeAll is called then subsequent store for same url works correctly`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val originalResponse = TestDataFactory.createCachedResponse(
            url = url.toString(),
            body = "original"
        )
        storage.store(url, originalResponse)
        storage.removeAll(url)

        // When
        val newResponse = TestDataFactory.createCachedResponse(
            url = url.toString(),
            body = "new data after removeAll"
        )
        storage.store(url, newResponse)

        // Then
        val retrieved = storage.find(url, emptyMap())
        assertNotNull(retrieved, "Should be able to store after removeAll")
        assertEquals(
            "new data after removeAll",
            retrieved.body.decodeToString(),
            "Should retrieve the new data"
        )
    }

    @Test
    fun `given removeAll behavior when comparing to remove then both delete correctly`() = runTest {
        // Given
        val url1 = Url("https://api.example.com/for-remove")
        val url2 = Url("https://api.example.com/for-removeAll")

        storage.store(url1, TestDataFactory.createCachedResponse(url = url1.toString()))
        storage.store(url2, TestDataFactory.createCachedResponse(url = url2.toString()))

        // When
        storage.remove(url1, emptyMap())
        storage.removeAll(url2)

        // Then - Both should result in deleted entries
        assertNull(storage.find(url1, emptyMap()), "remove() should delete the entry")
        assertNull(storage.find(url2, emptyMap()), "removeAll() should delete the entry")

        // Cache directory should remain intact
        assertTrue(
            fakeFileSystem.directoryExists("/fake/cache/http_cache".toOkioPath()),
            "Cache directory should remain intact after both operations"
        )
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
