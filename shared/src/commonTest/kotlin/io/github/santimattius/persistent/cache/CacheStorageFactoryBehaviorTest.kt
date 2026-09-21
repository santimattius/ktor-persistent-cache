@file:Suppress("DEPRECATION")

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.github.santimattius.persistent.cache.doubles.TestClock
import io.github.santimattius.persistent.cache.doubles.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Behavioral (regression) tests for the deprecated [CacheStorageFactory] facade: two independent
 * [CacheStorage][io.ktor.client.plugins.cache.storage.CacheStorage] instances built via
 * [CacheStorageFactory.create] for the SAME on-disk directory must be interoperable, exactly as
 * they were when [CacheStorageFactory] built `OkioFileCacheStorage` directly.
 *
 * REWRITTEN for task 2.8: the previous version manually constructed `OkioFileCacheStorage` with
 * an equivalent `OkioFileCacheConfig` over the hand-rolled `doubles/FakeFileSystem.kt` to prove
 * the factory's wiring matched a manual build. Both types are deleted in this PR (task 2.5
 * removes `OkioFileCacheStorage`/`OkioFileCacheConfig`; task 2.8 removes `FakeFileSystem`, which
 * only worked as an `okio.FileSystem` double and could never move to `cache-core`). This version
 * proves the same cross-instance interoperability property using only the still-public facade
 * API, against a real temp directory (matching the pattern already used by
 * `cache-okio/commonTest/AuthPluginInteropTest.kt`).
 */
class CacheStorageFactoryBehaviorTest {

    private lateinit var cacheRootDir: Path
    private lateinit var provider: FakeCacheDirectoryProvider
    private lateinit var clock: TestClock

    @BeforeTest
    fun setUp() {
        val uniqueSuffix = Random.nextLong().toString()
        cacheRootDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor-cache-factory-behavior-test-$uniqueSuffix"
        FileSystem.SYSTEM.createDirectories(cacheRootDir)
        provider = FakeCacheDirectoryProvider(cacheRootDir.toString())
        clock = TestClock(1_000_000L)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(cacheRootDir, mustExist = false)
    }

    @Test
    fun `given entry stored via one factory instance when reading with another instance then response matches`() =
        runTest {
            val cacheConfig = CacheConfig(
                enabled = true,
                cacheDirectory = "app_http_cache",
                maxCacheSize = 8L * 1024 * 1024,
                cacheTtl = 120 * 60 * 1000
            )

            val storageA = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = FileSystem.SYSTEM,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )
            val storageB = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = FileSystem.SYSTEM,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )

            val url = Url("https://example.com/sync-check")
            val data = TestDataFactory.createCachedResponse(url = url.toString(), body = "payload-a")

            storageA.store(url, data)

            val retrieved = storageB.find(url, emptyMap())
            assertNotNull(retrieved)
            assertEquals("payload-a", retrieved.body.decodeToString())
        }

    @Test
    fun `given entry stored via second factory instance when reading via the first then response matches`() =
        runTest {
            val cacheConfig = CacheConfig(
                enabled = true,
                cacheDirectory = "shared_volume",
                maxCacheSize = 4L * 1024 * 1024,
                cacheTtl = 45 * 60 * 1000
            )

            val storageA = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = FileSystem.SYSTEM,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )
            val storageB = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = FileSystem.SYSTEM,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )

            val url = Url("https://example.com/sync-check-reverse")
            val data = TestDataFactory.createCachedResponse(url = url.toString(), body = "payload-b")

            storageB.store(url, data)

            val retrieved = storageA.find(url, emptyMap())
            assertNotNull(retrieved)
            assertEquals("payload-b", retrieved.body.decodeToString())
        }
}
