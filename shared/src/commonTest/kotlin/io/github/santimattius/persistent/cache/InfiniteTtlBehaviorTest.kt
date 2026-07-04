package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.github.santimattius.persistent.cache.doubles.FakeFileSystem
import io.github.santimattius.persistent.cache.doubles.TestClock
import io.github.santimattius.persistent.cache.doubles.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Behavioral tests for the "never expires" TTL convention.
 *
 * Per spec (issue #25), `config.ttl <= 0` must mean the entry NEVER expires,
 * mirroring the existing `maxSize <= 0` = "unlimited" convention. This is
 * verified across both [OkioFileCacheStorage.find] and [OkioFileCacheStorage.findAll]
 * after advancing the clock far beyond any realistic TTL window.
 */
class InfiniteTtlBehaviorTest {

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
    fun `given ttl of zero when clock advances far past storage then find still returns entry`() =
        runTest {
            // Given - ttl = 0 means "never expires"
            val clock = TestClock()
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 0,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem, clock::now)

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
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = -1,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem, clock::now)

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
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 0,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem, clock::now)

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
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = -1,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem, clock::now)

            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // When - Advance clock far beyond any realistic TTL window
            clock.advance(10_000_000)

            // Then - findAll must still return the entry (never expires)
            val results = storage.findAll(url)
            assertNotNull(results.firstOrNull(), "Entry with ttl=-1 must never expire via findAll")
        }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
