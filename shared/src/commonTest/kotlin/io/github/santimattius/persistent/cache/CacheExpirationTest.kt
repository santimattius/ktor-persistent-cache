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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for cache expiration (TTL) functionality.
 *
 * These tests verify that cache entries expire correctly based on TTL configuration.
 * Uses Given-When-Then pattern for clarity.
 */
class CacheExpirationTest {

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
    fun `given a cached entry when ttl has not expired then entry is returned`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000, // 1 hour TTL
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())

        // When
        storage.store(url, response)
        val result = storage.find(url, emptyMap())

        // Then
        assertNotNull(result, "Entry should be returned when TTL has not expired")
    }

    @Test
    fun `given a very short ttl when entry is accessed after expiration then returns null`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 50, // 50 millisecond TTL
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())

        // When
        storage.store(url, response)

        // Use real time delay (not virtual time) by switching to Default dispatcher
        withContext(Dispatchers.Default) {
            delay(100) // Wait longer than TTL
        }

        val result = storage.find(url, emptyMap())

        // Then
        assertNull(result, "Entry should be null after TTL expires")
    }

    @Test
    fun `given expired entry when find is called then entry file is deleted`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 50, // 50 millisecond TTL
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, response)

        // Verify file exists
        val filesBeforeExpiration = fakeFileSystem.getAllFiles().filter {
            it.toString().contains("http_cache") && it.name.endsWith(".cache")
        }
        assertTrue(filesBeforeExpiration.isNotEmpty(), "Cache file should exist after store")

        // When - wait for expiration using real time and access
        withContext(Dispatchers.Default) {
            delay(100) // Wait longer than TTL
        }
        storage.find(url, emptyMap())

        // Then - file should be deleted
        val filesAfterExpiration = fakeFileSystem.getAllFiles().filter {
            it.toString().contains("http_cache") && it.name.endsWith(".cache")
        }
        assertTrue(filesAfterExpiration.isEmpty(), "Expired cache file should be deleted")
    }

    @Test
    fun `given expired entry when findAll is called then returns empty set`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 50, // 50 millisecond TTL
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )
        val storage = OkioFileCacheStorage(config, fakeFileSystem)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, response)

        // When - wait for expiration using real time
        withContext(Dispatchers.Default) {
            delay(100) // Wait longer than TTL
        }
        val results = storage.findAll(url)

        // Then
        assertTrue(results.isEmpty(), "findAll should return empty set for expired entry")
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
