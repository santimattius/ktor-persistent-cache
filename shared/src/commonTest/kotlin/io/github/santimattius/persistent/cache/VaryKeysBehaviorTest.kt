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
 * Behavioral tests for VaryKeys support in cache storage.
 *
 * These tests verify that the cache correctly differentiates between requests
 * with the same URL but different Vary headers (e.g., Accept-Language, Accept-Encoding).
 *
 * Uses Given-When-Then pattern for clarity.
 */
class VaryKeysBehaviorTest {

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

    // ========================================================================
    // STORE WITH VARY KEYS TESTS
    // ========================================================================

    @Test
    fun `given same url with different varyKeys when stored then creates separate cache entries`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val responseEnglish = TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Hello World",
            varyKeys = mapOf("Accept-Language" to "en")
        )
        val responseSpanish = TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Hola Mundo",
            varyKeys = mapOf("Accept-Language" to "es")
        )

        // When
        storage.store(url, responseEnglish)
        storage.store(url, responseSpanish)

        // Then - Both entries should exist as separate files
        val cacheFiles = fakeFileSystem.getAllFiles().filter { it.name.endsWith(".cache") }
        assertEquals(2, cacheFiles.size, "Should have two separate cache files")
    }

    @Test
    fun `given same url with same varyKeys when stored twice then overwrites entry`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val varyKeys = mapOf("Accept-Language" to "en")
        val originalResponse = TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Original content",
            varyKeys = varyKeys
        )
        val updatedResponse = TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Updated content",
            varyKeys = varyKeys
        )

        // When
        storage.store(url, originalResponse)
        storage.store(url, updatedResponse)

        // Then - Should have only one file (overwritten)
        val cacheFiles = fakeFileSystem.getAllFiles().filter { it.name.endsWith(".cache") }
        assertEquals(1, cacheFiles.size, "Should have one cache file (overwritten)")

        val retrieved = storage.find(url, varyKeys)
        assertNotNull(retrieved)
        assertEquals("Updated content", retrieved.body.decodeToString())
    }

    // ========================================================================
    // FIND WITH VARY KEYS TESTS
    // ========================================================================

    @Test
    fun `given cached entries with different varyKeys when finding with specific varyKeys then returns correct entry`() = runTest {
        // Given
        val url = Url("https://api.example.com/content")
        val varyKeysEnglish = mapOf("Accept-Language" to "en")
        val varyKeysSpanish = mapOf("Accept-Language" to "es")

        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "English content",
            varyKeys = varyKeysEnglish
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Spanish content",
            varyKeys = varyKeysSpanish
        ))

        // When
        val englishResult = storage.find(url, varyKeysEnglish)
        val spanishResult = storage.find(url, varyKeysSpanish)

        // Then
        assertNotNull(englishResult, "English entry should be found")
        assertNotNull(spanishResult, "Spanish entry should be found")
        assertEquals("English content", englishResult.body.decodeToString())
        assertEquals("Spanish content", spanishResult.body.decodeToString())
    }

    @Test
    fun `given cached entry with varyKeys when finding with different varyKeys then returns null`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val storedVaryKeys = mapOf("Accept-Language" to "en")
        val queryVaryKeys = mapOf("Accept-Language" to "fr")

        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "English content",
            varyKeys = storedVaryKeys
        ))

        // When
        val result = storage.find(url, queryVaryKeys)

        // Then
        assertNull(result, "Should not find entry with different varyKeys")
    }

    @Test
    fun `given cached entry without varyKeys when finding with empty varyKeys then returns entry`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString(), body = "No vary keys")
        storage.store(url, response)

        // When
        val result = storage.find(url, emptyMap())

        // Then
        assertNotNull(result)
        assertEquals("No vary keys", result.body.decodeToString())
    }

    // ========================================================================
    // FIND ALL TESTS
    // ========================================================================

    @Test
    fun `given multiple cached entries with different varyKeys when findAll then returns all entries for url`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "English",
            varyKeys = mapOf("Accept-Language" to "en")
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Spanish",
            varyKeys = mapOf("Accept-Language" to "es")
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "French",
            varyKeys = mapOf("Accept-Language" to "fr")
        ))

        // When
        val results = storage.findAll(url)

        // Then
        assertEquals(3, results.size, "Should return all three cached entries")
        val bodies = results.map { it.body.decodeToString() }.toSet()
        assertTrue(bodies.contains("English"))
        assertTrue(bodies.contains("Spanish"))
        assertTrue(bodies.contains("French"))
    }

    @Test
    fun `given entries for different urls when findAll then returns only entries for specified url`() = runTest {
        // Given
        val url1 = Url("https://api.example.com/users")
        val url2 = Url("https://api.example.com/posts")

        storage.store(url1, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url1.toString(),
            body = "Users EN",
            varyKeys = mapOf("Accept-Language" to "en")
        ))
        storage.store(url1, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url1.toString(),
            body = "Users ES",
            varyKeys = mapOf("Accept-Language" to "es")
        ))
        storage.store(url2, TestDataFactory.createCachedResponse(
            url = url2.toString(),
            body = "Posts"
        ))

        // When
        val url1Results = storage.findAll(url1)
        val url2Results = storage.findAll(url2)

        // Then
        assertEquals(2, url1Results.size, "Should return 2 entries for url1")
        assertEquals(1, url2Results.size, "Should return 1 entry for url2")
    }

    // ========================================================================
    // REMOVE WITH VARY KEYS TESTS
    // ========================================================================

    @Test
    fun `given cached entries with different varyKeys when removing specific varyKeys then only that entry is removed`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val varyKeysEnglish = mapOf("Accept-Language" to "en")
        val varyKeysSpanish = mapOf("Accept-Language" to "es")

        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "English",
            varyKeys = varyKeysEnglish
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Spanish",
            varyKeys = varyKeysSpanish
        ))

        // When
        storage.remove(url, varyKeysEnglish)

        // Then
        assertNull(storage.find(url, varyKeysEnglish), "English entry should be removed")
        assertNotNull(storage.find(url, varyKeysSpanish), "Spanish entry should still exist")
    }

    // ========================================================================
    // REMOVE ALL TESTS
    // ========================================================================

    @Test
    fun `given cached entries with different varyKeys when removeAll then all entries for url are removed`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "English",
            varyKeys = mapOf("Accept-Language" to "en")
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Spanish",
            varyKeys = mapOf("Accept-Language" to "es")
        ))
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "French",
            varyKeys = mapOf("Accept-Language" to "fr")
        ))

        // Verify all exist
        assertEquals(3, storage.findAll(url).size)

        // When
        storage.removeAll(url)

        // Then
        assertTrue(storage.findAll(url).isEmpty(), "All entries should be removed")
    }

    @Test
    fun `given entries for multiple urls when removeAll for one url then other url entries remain`() = runTest {
        // Given
        val url1 = Url("https://api.example.com/users")
        val url2 = Url("https://api.example.com/posts")

        storage.store(url1, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url1.toString(),
            body = "Users EN",
            varyKeys = mapOf("Accept-Language" to "en")
        ))
        storage.store(url2, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url2.toString(),
            body = "Posts EN",
            varyKeys = mapOf("Accept-Language" to "en")
        ))

        // When
        storage.removeAll(url1)

        // Then
        assertTrue(storage.findAll(url1).isEmpty(), "url1 entries should be removed")
        assertEquals(1, storage.findAll(url2).size, "url2 entries should remain")
    }

    // ========================================================================
    // MULTIPLE VARY KEYS TESTS
    // ========================================================================

    @Test
    fun `given entry with multiple varyKeys when finding with same keys then returns entry`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val varyKeys = mapOf(
            "Accept-Language" to "en",
            "Accept-Encoding" to "gzip"
        )
        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Compressed English",
            varyKeys = varyKeys
        ))

        // When
        val result = storage.find(url, varyKeys)

        // Then
        assertNotNull(result)
        assertEquals("Compressed English", result.body.decodeToString())
    }

    @Test
    fun `given entry with multiple varyKeys when finding with partial keys then returns null`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val fullVaryKeys = mapOf(
            "Accept-Language" to "en",
            "Accept-Encoding" to "gzip"
        )
        val partialVaryKeys = mapOf("Accept-Language" to "en")

        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Compressed English",
            varyKeys = fullVaryKeys
        ))

        // When
        val result = storage.find(url, partialVaryKeys)

        // Then
        assertNull(result, "Should not find entry with partial varyKeys")
    }

    // ========================================================================
    // VARY KEYS ORDERING TESTS
    // ========================================================================

    @Test
    fun `given varyKeys in different order when stored and retrieved then matches correctly`() = runTest {
        // Given
        val url = Url("https://api.example.com/data")
        val varyKeysStored = mapOf(
            "Accept-Encoding" to "gzip",
            "Accept-Language" to "en"
        )
        val varyKeysQuery = mapOf(
            "Accept-Language" to "en",
            "Accept-Encoding" to "gzip"
        )

        storage.store(url, TestDataFactory.createCachedResponseWithVaryKeys(
            url = url.toString(),
            body = "Test content",
            varyKeys = varyKeysStored
        ))

        // When - Query with different key order
        val result = storage.find(url, varyKeysQuery)

        // Then - Should find the entry because keys are sorted internally
        assertNotNull(result, "Should find entry regardless of varyKeys order")
        assertEquals("Test content", result.body.decodeToString())
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
