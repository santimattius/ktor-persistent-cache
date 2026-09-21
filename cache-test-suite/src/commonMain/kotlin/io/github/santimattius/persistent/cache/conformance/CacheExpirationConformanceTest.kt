package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.TestClock
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for cache expiration (TTL) functionality.
 *
 * Relocated from `shared/commonTest/CacheExpirationTest.kt` (design decision #5, Engram #1505).
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class CacheExpirationConformanceTest<P> : CacheConformanceSupport<P>() {

    @Test
    fun `given a cached entry when ttl has not expired then entry is returned`() = runTest {
        // Given
        val clock = TestClock()
        val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 60 * 60 * 1000, clock = clock::now)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())

        // When
        storage.store(url, response)
        val result = storage.find(url, emptyMap())

        // Then
        assertNotNull(result, "Entry should be returned when TTL has not expired")
    }

    @Test
    fun `given a very short ttl when entry is accessed after expiration then returns null`() =
        runTest {
            // Given
            val clock = TestClock()
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 50, clock = clock::now)

            val url = Url("https://api.example.com/data")
            val response = TestDataFactory.createCachedResponse(url = url.toString())

            // When
            storage.store(url, response)
            clock.advance(100) // Advance past TTL
            val result = storage.find(url, emptyMap())

            // Then
            assertNull(result, "Entry should be null after TTL expires")
        }

    @Test
    fun `given expired entry when find is called then entry file is deleted`() = runTest {
        // Given
        val clock = TestClock()
        val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 50, clock = clock::now)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, response)

        // Verify file exists
        assertTrue(cacheFiles().isNotEmpty(), "Cache file should exist after store")

        // When - advance past TTL and access
        clock.advance(100)
        storage.find(url, emptyMap())

        // Then - file should be deleted
        assertTrue(cacheFiles().isEmpty(), "Expired cache file should be deleted")
    }

    @Test
    fun `given expired entry when findAll is called then returns empty set`() = runTest {
        // Given
        val clock = TestClock()
        val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 50, clock = clock::now)

        val url = Url("https://api.example.com/data")
        val response = TestDataFactory.createCachedResponse(url = url.toString())
        storage.store(url, response)

        // When - advance past TTL
        clock.advance(100)
        val results = storage.findAll(url)

        // Then
        assertTrue(results.isEmpty(), "findAll should return empty set for expired entry")
    }
}
