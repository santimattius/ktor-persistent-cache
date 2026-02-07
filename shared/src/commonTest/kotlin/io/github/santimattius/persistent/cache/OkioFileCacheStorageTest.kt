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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for OkioFileCacheStorage.
 *
 * These tests verify the correct behavior of the cache storage implementation
 * using the Given-When-Then pattern for clarity and readability.
 *
 * Test doubles used:
 * - FakeFileSystem: In-memory file system for isolated testing
 * - FakeCacheDirectoryProvider: Configurable cache directory provider
 */
class OkioFileCacheStorageTest {

    private lateinit var fakeFileSystem: FakeFileSystem
    private lateinit var storage: OkioFileCacheStorage
    private lateinit var config: OkioFileCacheConfig

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        // Create the base cache directory
        fakeFileSystem.createDirectory("/fake/cache".toOkioPath())

        config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024, // 10 MB
            ttl = 60 * 60 * 1000, // 1 hour
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )

        storage = OkioFileCacheStorage(config, fakeFileSystem)
    }

    @AfterTest
    fun tearDown() {
        fakeFileSystem.clear()
    }

    @Test
    fun `given a cached response when stored then it can be retrieved`() = runTest {
        // Given
        val url = Url("https://api.example.com/users")
        val cachedResponse = TestDataFactory.createCachedResponse(
            url = url.toString(),
            body = "user data response"
        )

        // When
        storage.store(url, cachedResponse)

        // Then
        val retrieved = storage.find(url, emptyMap())
        assertNotNull(retrieved, "Retrieved response should not be null")
        assertEquals(url, retrieved.url, "URL should match")
        assertEquals(
            "user data response",
            retrieved.body.decodeToString(),
            "Body content should match"
        )
    }

    @Test
    fun `given no cached response when finding by url then returns null`() = runTest {
        // Given
        val url = Url("https://api.example.com/nonexistent")
        // No cache entry stored

        // When
        val result = storage.find(url, emptyMap())

        // Then
        assertNull(result, "Should return null for non-existent cache entry")
    }

    @Test
    fun `given multiple cached responses when stored then each can be retrieved independently`() =
        runTest {
            // Given
            val url1 = Url("https://api.example.com/users")
            val url2 = Url("https://api.example.com/posts")
            val response1 =
                TestDataFactory.createCachedResponse(url = url1.toString(), body = "users")
            val response2 =
                TestDataFactory.createCachedResponse(url = url2.toString(), body = "posts")

            // When
            storage.store(url1, response1)
            storage.store(url2, response2)

            // Then
            val retrieved1 = storage.find(url1, emptyMap())
            val retrieved2 = storage.find(url2, emptyMap())

            assertNotNull(retrieved1)
            assertNotNull(retrieved2)
            assertEquals("users", retrieved1.body.decodeToString())
            assertEquals("posts", retrieved2.body.decodeToString())
        }

    @Test
    fun `given a cached response when findAll is called then returns set containing the response`() =
        runTest {
            // Given
            val url = Url("https://api.example.com/data")
            val cachedResponse = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "data response"
            )
            storage.store(url, cachedResponse)

            // When
            val results = storage.findAll(url)

            // Then
            assertEquals(1, results.size, "Should return set with one entry")
            assertEquals("data response", results.first().body.decodeToString())
        }

    @Test
    fun `given no cached response when findAll is called then returns empty set`() = runTest {
        // Given
        val url = Url("https://api.example.com/missing")
        // No cache entry stored

        // When
        val results = storage.findAll(url)

        // Then
        assertTrue(results.isEmpty(), "Should return empty set for non-existent URL")
    }

    @Test
    fun `given a cached response when removed then it can no longer be found`() = runTest {
        // Given
        val url = Url("https://api.example.com/users")
        val cachedResponse = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, cachedResponse)

        // Verify it exists first
        assertNotNull(storage.find(url, emptyMap()), "Cache should exist before removal")

        // When
        storage.remove(url, emptyMap())

        // Then
        assertNull(storage.find(url, emptyMap()), "Cache should be null after removal")
    }

    @Test
    fun `given no cached response when remove is called then no error occurs`() = runTest {
        // Given
        val url = Url("https://api.example.com/nonexistent")
        // No cache entry stored

        // When & Then (should not throw)
        storage.remove(url, emptyMap())
    }

    @Test
    fun `given a cached response when removeAll is called then entry is deleted`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val cachedResponse = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, cachedResponse)

        // Verify it exists
        assertNotNull(storage.find(url, emptyMap()))

        // When
        storage.removeAll(url)

        // Then
        assertNull(storage.find(url, emptyMap()), "Cache should be deleted after removeAll")
    }

    @Test
    fun `given multiple urls cached when removeAll is called for one url then only that entry is removed`() =
        runTest {
            // Given
            val url1 = Url("https://api.example.com/users")
            val url2 = Url("https://api.example.com/posts")
            storage.store(
                url1,
                TestDataFactory.createCachedResponse(url = url1.toString(), body = "users")
            )
            storage.store(
                url2,
                TestDataFactory.createCachedResponse(url = url2.toString(), body = "posts")
            )

            // When
            storage.removeAll(url1)

            // Then
            assertNull(storage.find(url1, emptyMap()), "Removed URL should not be found")
            assertNotNull(storage.find(url2, emptyMap()), "Other URL should still exist")
        }

    @Test
    fun `given an existing cache entry when stored again with new data then entry is updated`() =
        runTest {
            // Given
            val url = Url("https://api.example.com/data")
            val originalResponse = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "original data"
            )
            storage.store(url, originalResponse)

            // When
            val updatedResponse = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "updated data"
            )
            storage.store(url, updatedResponse)

            // Then
            val retrieved = storage.find(url, emptyMap())
            assertNotNull(retrieved)
            assertEquals(
                "updated data",
                retrieved.body.decodeToString(),
                "Should return updated data"
            )
        }

    @Test
    fun `given store operation when cleanup runs internally then no deadlock occurs`() = runTest {
        // Given
        // Configure small max size to trigger cleanup
        val smallConfig = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 100, // Very small to trigger cleanup
            ttl = 60 * 60 * 1000,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storageWithCleanup = OkioFileCacheStorage(smallConfig, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(
            url = url.toString(),
            body = "test data for cleanup verification"
        )

        // When - This should not deadlock (the fix removes nested mutex acquisition)
        storageWithCleanup.store(url, response)

        // Then - If we reach here, no deadlock occurred
        // The test will timeout if deadlock happens
        assertTrue(true, "Store operation completed without deadlock")
    }

    @Test
    fun `given multiple store operations when executed sequentially then all complete without deadlock`() =
        runTest {
            // Given
            val smallConfig = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 500, // Small size to ensure cleanup runs
                ttl = 60 * 60 * 1000,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storageWithCleanup = OkioFileCacheStorage(smallConfig, fakeFileSystem)

            // When - Multiple stores, each triggering cleanup
            repeat(5) { i ->
                val url = Url("https://api.example.com/data/$i")
                val response = TestDataFactory.createCachedResponse(
                    url = url.toString(),
                    body = "data for request $i with some additional content"
                )
                storageWithCleanup.store(url, response)
            }

            // Then - All operations completed (no deadlock)
            assertTrue(true, "All store operations completed without deadlock")
        }

    @Test
    fun `given cache storage when initialized then cache directory is created`() = runTest {
        // Given & When (initialization happens in setUp)
        // Storage is already created in setUp()

        // Then
        val cacheDir = "/fake/cache/http_cache".toOkioPath()
        assertTrue(
            fakeFileSystem.directoryExists(cacheDir),
            "Cache directory should be created on initialization"
        )
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
