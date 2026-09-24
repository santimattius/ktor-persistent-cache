package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.TestClock
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Behavioral tests for the "never expires" TTL convention.
 *
 * Per spec (issue #25), `ttl <= 0` must mean the entry NEVER expires, mirroring the existing
 * `maxSize <= 0` = "unlimited" convention. This is verified across both [find] and [findAll]
 * after advancing the clock far beyond any realistic TTL window.
 *
 * Relocated from `shared/commonTest/InfiniteTtlBehaviorTest.kt` (design decision #5, Engram #1505).
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class InfiniteTtlConformanceTest<P> : CacheConformanceSupport<P>() {

    @Test
    fun `given ttl of zero when clock advances far past storage then find still returns entry`() =
        runTest {
            // Given - ttl = 0 means "never expires"
            val clock = TestClock()
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 0, clock = clock::now)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Advance clock far beyond any realistic TTL window
            clock.advance(10_000_000)

            // Then - Entry must still be found (never expires)
            val result = storage.find(url, emptyMap())
            assertNotNull(result, "Entry with ttl=0 must never expire")
        }

    @Test
    fun `given ttl of negative one when clock advances far past storage then find still returns entry`() =
        runTest {
            // Given - ttl < 0 also means "never expires"
            val clock = TestClock()
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = -1, clock = clock::now)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Advance clock far beyond any realistic TTL window
            clock.advance(10_000_000)

            // Then - Entry must still be found (never expires)
            val result = storage.find(url, emptyMap())
            assertNotNull(result, "Entry with ttl=-1 must never expire")
        }

    @Test
    fun `given ttl of zero when clock advances far past storage then findAll still returns entry`() =
        runTest {
            // Given - ttl = 0 means "never expires"
            val clock = TestClock()
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = 0, clock = clock::now)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Advance clock far beyond any realistic TTL window
            clock.advance(10_000_000)

            // Then - findAll must still return the entry (never expires)
            val results = storage.findAll(url)
            assertNotNull(results.firstOrNull(), "Entry with ttl=0 must never expire via findAll")
        }

    @Test
    fun `given ttl of negative one when clock advances far past storage then findAll still returns entry`() =
        runTest {
            // Given - ttl < 0 also means "never expires"
            val clock = TestClock()
            val storage = newStorage(maxSize = 10L * 1024 * 1024, ttl = -1, clock = clock::now)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Advance clock far beyond any realistic TTL window
            clock.advance(10_000_000)

            // Then - findAll must still return the entry (never expires)
            val results = storage.findAll(url)
            assertNotNull(results.firstOrNull(), "Entry with ttl=-1 must never expire via findAll")
        }
}
