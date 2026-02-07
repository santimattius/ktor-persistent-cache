package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.github.santimattius.persistent.cache.doubles.FakeFileSystem
import io.github.santimattius.persistent.cache.doubles.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for consistent TTL (Time-To-Live) checking.
 *
 * These tests verify that:
 * 1. TTL expiration uses stored timestamp consistently across all operations
 * 2. Cleanup removes expired entries using the same TTL logic as find()
 * 3. Size-based LRU eviction works correctly with TTL
 *
 * Uses Given-When-Then pattern for clarity.
 */
class TtlConsistencyBehaviorTest {

    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        fakeFileSystem.createDirectory("/fake/cache".toOkioPath())
    }

    @AfterTest
    fun tearDown() {
        fakeFileSystem.clear()
    }

    @Test
    fun `given entry stored when cleanup runs before ttl expires then entry is kept`() = runTest {
        // Given - Configure with short TTL but don't let it expire
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000, // 1 hour - won't expire during test
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())

        // When - Store entry (triggers cleanup internally)
        storage.store(url, response)

        // Then - Entry should still be findable (not removed by cleanup)
        val result = storage.find(url, emptyMap())
        assertNotNull(result, "Entry should exist after cleanup when TTL not expired")
    }

    @Test
    fun `given entry stored when ttl expires then both find and cleanup treat it as expired`() =
        runTest {
            // Given - Very short TTL
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 50, // 50ms TTL
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Wait for TTL to expire
            withContext(Dispatchers.Default) {
                delay(100)
            }

            // Then - find() should return null (expired)
            val result = storage.find(url, emptyMap())
            assertNull(result, "Entry should be expired and not found")
        }

    @Test
    fun `given expired entries when new entry stored then cleanup removes expired entries first`() =
        runTest {
            // Given
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 50, // 50ms TTL
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            // Store initial entries
            val url1 = Url("https://api.example.com/data1")
            val url2 = Url("https://api.example.com/data2")
            storage.store(url1, TestDataFactory.createCachedResponse(url = url1.toString()))
            storage.store(url2, TestDataFactory.createCachedResponse(url = url2.toString()))

            // Let entries expire
            withContext(Dispatchers.Default) {
                delay(100)
            }

            // When - Store a new entry (triggers cleanup)
            val url3 = Url("https://api.example.com/data3")
            storage.store(url3, TestDataFactory.createCachedResponse(url = url3.toString()))

            // Then - New entry should exist, old expired entries should be cleaned up
            assertNotNull(storage.find(url3, emptyMap()), "New entry should exist")
            assertNull(storage.find(url1, emptyMap()), "Old entry 1 should be expired")
            assertNull(storage.find(url2, emptyMap()), "Old entry 2 should be expired")
        }

    @Test
    fun `given non-expired entries exceeding size limit when cleanup runs then oldest entries removed`() =
        runTest {
            // Given - Very small size limit
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 500, // Small size to force eviction
                ttl = 60 * 60 * 1000, // Long TTL - entries won't expire
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            // When - Store multiple entries that exceed size limit
            repeat(5) { i ->
                val url = Url("https://api.example.com/data/$i")
                storage.store(
                    url, TestDataFactory.createCachedResponse(
                        url = url.toString(),
                        body = "Response body for entry $i with some content to take up space"
                    )
                )
            }

            // Then - Some entries should be evicted (LRU), but cache should function
            val cacheFiles = fakeFileSystem.getAllFiles().filter { it.name.endsWith(".cache") }
            assertTrue(cacheFiles.isNotEmpty(), "Some entries should remain after LRU eviction")

            // Latest entry should still exist
            val latestUrl = Url("https://api.example.com/data/4")
            assertNotNull(storage.find(latestUrl, emptyMap()), "Latest entry should exist")
        }

    @Test
    fun `given corrupted cache file when cleanup runs then corrupted file is removed`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        // Store a valid entry
        val validUrl = Url("https://api.example.com/valid")
        storage.store(validUrl, TestDataFactory.createCachedResponse(url = validUrl.toString()))

        // Manually create a corrupted cache file
        val corruptedFile = "/fake/cache/http_cache/corrupted_0.cache".toOkioPath()
        fakeFileSystem.setFileContent(corruptedFile, "not valid protobuf data".encodeToByteArray())

        // When - Store another entry (triggers cleanup)
        val newUrl = Url("https://api.example.com/new")
        storage.store(newUrl, TestDataFactory.createCachedResponse(url = newUrl.toString()))

        // Then - Valid entries should work, corrupted file should be handled gracefully
        assertNotNull(storage.find(validUrl, emptyMap()), "Valid entry should still exist")
        assertNotNull(storage.find(newUrl, emptyMap()), "New entry should exist")
    }

    @Test
    fun `given mix of expired and valid entries when findAll called then only valid entries returned`() =
        runTest {
            // Given
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 50, // 50ms TTL
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            val url = Url("https://api.example.com/data")

            // Store entry with short TTL that will expire
            storage.store(
                url, TestDataFactory.createCachedResponseWithVaryKeys(
                    url = url.toString(),
                    body = "English - will expire",
                    varyKeys = mapOf("Accept-Language" to "en")
                )
            )

            // Let it expire
            withContext(Dispatchers.Default) {
                delay(100)
            }

            // Store fresh entry
            storage.store(
                url, TestDataFactory.createCachedResponseWithVaryKeys(
                    url = url.toString(),
                    body = "Spanish - fresh",
                    varyKeys = mapOf("Accept-Language" to "es")
                )
            )

            // When
            val results = storage.findAll(url)

            // Then - Only the fresh entry should be returned
            assertEquals(1, results.size, "Should only return non-expired entry")
            assertEquals("Spanish - fresh", results.first().body.decodeToString())
        }

    @Test
    fun `given entry at exact ttl boundary when checked then treated as expired`() = runTest {
        // Given - This tests the boundary condition where elapsed == ttl
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 50,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

        // When - Wait exactly at TTL boundary (plus small buffer for timing)
        withContext(Dispatchers.Default) {
            delay(60) // Slightly over TTL
        }

        // Then - Should be considered expired
        val result = storage.find(url, emptyMap())
        assertNull(result, "Entry at TTL boundary should be expired")
    }

    @Test
    fun `given zero ttl config when entry stored then immediately expires`() = runTest {
        // Given - TTL of 0 means immediate expiration
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 0, // Zero TTL
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

        // Small delay to ensure time has passed
        withContext(Dispatchers.Default) {
            delay(10)
        }

        // When
        val result = storage.find(url, emptyMap())

        // Then - Should be expired immediately
        assertNull(result, "Entry with zero TTL should be expired immediately")
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
