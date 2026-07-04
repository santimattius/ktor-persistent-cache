package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.github.santimattius.persistent.cache.doubles.TestClock
import io.github.santimattius.persistent.cache.doubles.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cold-start (app-restart) persistence tests for [OkioFileCacheStorage].
 *
 * Per spec (issue #25, domain `okio-file-cache-storage`), a NEW storage
 * instance built with the REAL filesystem ([FileSystem.SYSTEM]) pointed at
 * an existing cache directory MUST read entries written there by a prior,
 * discarded instance. This simulates what actually happens across an app
 * restart: a fresh process, a fresh object graph, but the same on-disk
 * directory.
 *
 * Deliberately uses [FileSystem.SYSTEM] (not `FakeFileSystem`, which is
 * in-memory and would trivially "persist" within a single process/test and
 * would never prove cross-instance, on-disk durability). The clock is still
 * injected (a [TestClock]) so the TTL-boundary scenario stays deterministic
 * -- only the filesystem is real.
 *
 * Kept in `commonTest` (not `jvmTest`-only) per design: `FileSystem.SYSTEM`
 * and `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` are backed by a real,
 * writable directory on the JVM, Android-unit, and iOS-native test targets,
 * so this test exercises every host this library ships to instead of only
 * the JVM. If `iosSimulatorArm64Test` fails here specifically because the
 * simulator can't resolve/write to a temp directory (not an unrelated
 * flake), the pre-approved fallback documented in tasks/design is to move
 * this file to `jvmTest`-only and record the reason here.
 */
class ColdStartPersistenceTest {

    private lateinit var cacheRootDir: Path

    @BeforeTest
    fun setUp() {
        val uniqueSuffix = Random.nextLong().toString()
        cacheRootDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor-cache-cold-start-test-$uniqueSuffix"
        FileSystem.SYSTEM.createDirectories(cacheRootDir)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(cacheRootDir, mustExist = false)
    }

    @Test
    fun `given entry written by a discarded instance when a fresh instance reads same dir then entry is returned`() =
        runTest {
            // Given - instance A writes an entry to a real, on-disk cache directory
            val clock = TestClock()
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000, // 1 hour - won't expire during this test
                cacheDirectoryProvider = FakeCacheDirectoryProvider(cacheRootDir.toString())
            )
            val instanceA = OkioFileCacheStorage(config, FileSystem.SYSTEM, clock::now)

            val url = Url("https://api.example.com/cold-start")
            val response = TestDataFactory.createCachedResponse(
                url = url.toString(),
                body = "persisted across restart"
            )
            instanceA.store(url, response)

            // instanceA is now discarded (goes out of scope) -- simulates process death.
            // Only the on-disk directory (cacheRootDir) survives, exactly like an app restart.

            // When - a brand-new instance is built pointed at the SAME real directory
            val instanceB = OkioFileCacheStorage(config, FileSystem.SYSTEM, clock::now)
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
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 500,
                cacheDirectoryProvider = FakeCacheDirectoryProvider(cacheRootDir.toString())
            )
            val instanceA = OkioFileCacheStorage(config, FileSystem.SYSTEM, clock::now)

            val url = Url("https://api.example.com/cold-start-ttl")
            instanceA.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

            // instanceA discarded; wall-clock time elapses past the ttl boundary between
            // "app shutdown" and the next cold start.
            clock.advance(501)

            // When - a fresh instance (same dir, same elapsed clock) reads at T + 501
            val instanceB = OkioFileCacheStorage(config, FileSystem.SYSTEM, clock::now)
            val result = instanceB.find(url, emptyMap())

            // Then - the entry must be treated as expired on the cold read
            assertNull(result, "Entry past its ttl boundary must be expired on a cold-start read")
        }
}
