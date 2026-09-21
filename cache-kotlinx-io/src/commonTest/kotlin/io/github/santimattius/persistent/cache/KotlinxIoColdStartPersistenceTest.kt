@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.support.TestClock
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cold-start (app-restart) persistence for [KotlinxIoCacheFileSystem]: proves data written by one
 * [KotlinxIoCacheFileSystem]-backed instance is readable by a FRESH instance pointed at the same
 * real, on-disk directory. This is the "real backend, not fake" contract that `cache-okio`'s own
 * cold-start test proves (design decision #5, Engram #1505) — required as an explicit task for
 * this backend because kotlinx-io ships no in-memory fake filesystem in 0.9.1, so every test in
 * this module already runs against a real directory; this test is the one that specifically
 * exercises a discarded-and-recreated instance, simulating a real app restart.
 *
 * Deliberately uses [KotlinxIoConformanceTestSupport.freshTempDirectory] (a real, writable temp
 * directory), not two [FileCacheStorage] instances sharing an in-process object — the clock is
 * still injected (a [TestClock]) so the TTL-boundary scenario stays deterministic, only the
 * filesystem is real.
 */
class KotlinxIoColdStartPersistenceTest {

    private lateinit var cacheRootDir: String

    @BeforeTest
    fun setUp() {
        cacheRootDir = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-cold-start")
    }

    @AfterTest
    fun tearDown() {
        KotlinxIoConformanceTestSupport.deleteRecursively(cacheRootDir)
    }

    @Test
    fun `given entry written by a discarded instance when a fresh instance reads same dir then entry is returned`() =
        runTest {
            // Given - instance A writes an entry to a real, on-disk cache directory
            val clock = TestClock()
            val instanceA = FileCacheStorage(
                fileSystem = KotlinxIoCacheFileSystem(),
                directoryRoot = cacheRootDir,
                directoryName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000, // 1 hour - won't expire during this test
                clock = clock::now
            )

            val url = Url("https://api.example.com/cold-start")
            val response = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "persisted across restart"
            )
            instanceA.store(url, response)

            // instanceA is now discarded (goes out of scope) -- simulates process death.
            // Only the on-disk directory (cacheRootDir) survives, exactly like an app restart.

            // When - a brand-new instance is built pointed at the SAME real directory
            val instanceB = FileCacheStorage(
                fileSystem = KotlinxIoCacheFileSystem(),
                directoryRoot = cacheRootDir,
                directoryName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000,
                clock = clock::now
            )
            val result = instanceB.find(url, emptyMap())

            // Then - the entry written by instance A must be readable by instance B
            assertNotNull(result, "Fresh instance must read entries written by a prior, discarded instance")
            assertEquals("persisted across restart", result.body.decodeToString())
        }

    @Test
    fun `given entry past ttl boundary when fresh instance reads on cold start then entry is treated as expired`() =
        runTest {
            // Given - instance A stores an entry with a short ttl at T
            val clock = TestClock()
            val instanceA = FileCacheStorage(
                fileSystem = KotlinxIoCacheFileSystem(),
                directoryRoot = cacheRootDir,
                directoryName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 500,
                clock = clock::now
            )

            val url = Url("https://api.example.com/cold-start-ttl")
            instanceA.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // instanceA discarded; wall-clock time elapses past the ttl boundary between
            // "app shutdown" and the next cold start.
            clock.advance(501)

            // When - a fresh instance (same dir, same elapsed clock) reads at T + 501
            val instanceB = FileCacheStorage(
                fileSystem = KotlinxIoCacheFileSystem(),
                directoryRoot = cacheRootDir,
                directoryName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 500,
                clock = clock::now
            )
            val result = instanceB.find(url, emptyMap())

            // Then - the entry must be treated as expired on the cold read
            assertNull(result, "Entry past its ttl boundary must be expired on a cold-start read")
        }
}
