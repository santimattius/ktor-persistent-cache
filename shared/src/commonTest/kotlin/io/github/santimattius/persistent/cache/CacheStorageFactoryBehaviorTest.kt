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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Behavioral tests for [CacheStorageFactory]: wiring from [CacheConfig] to on-disk behavior must match
 * a manually constructed [OkioFileCacheStorage] with equivalent [OkioFileCacheConfig] (same path the
 * [installPersistentCache] extension delegates to).
 */
class CacheStorageFactoryBehaviorTest {

    private lateinit var fakeFileSystem: FakeFileSystem
    private lateinit var provider: FakeCacheDirectoryProvider
    private lateinit var clock: TestClock

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        fakeFileSystem.createDirectory("/fake/cache".toOkioPath())
        provider = FakeCacheDirectoryProvider("/fake/cache")
        clock = TestClock(1_000_000L)
    }

    @AfterTest
    fun tearDown() {
        fakeFileSystem.clear()
    }

    private fun String.toOkioPath(): okio.Path = this.toPath()

    private fun equivalentManualStorage(cacheConfig: CacheConfig): OkioFileCacheStorage =
        OkioFileCacheStorage(
            config = OkioFileCacheConfig(
                fileName = cacheConfig.cacheDirectory,
                maxSize = cacheConfig.maxCacheSize,
                ttl = cacheConfig.cacheTtl,
                cacheDirectoryProvider = provider
            ),
            fileSystem = fakeFileSystem,
            clock = clock::now
        )

    @Test
    fun `given entry stored via factory when reading with manually built storage then response matches`() =
        runTest {
            val cacheConfig = CacheConfig(
                enabled = true,
                cacheDirectory = "app_http_cache",
                maxCacheSize = 8L * 1024 * 1024,
                cacheTtl = 120 * 60 * 1000
            )

            val factoryStorage = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = fakeFileSystem,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )
            val manualStorage = equivalentManualStorage(cacheConfig)

            val url = Url("https://example.com/sync-check")
            val data = TestDataFactory.createCachedResponse(url = url.toString(), body = "payload-a")

            factoryStorage.store(url, data)

            val retrieved = manualStorage.find(url, emptyMap())
            assertNotNull(retrieved)
            assertEquals("payload-a", retrieved.body.decodeToString())
        }

    @Test
    fun `given entry stored with manual Okio storage when reading via factory then response matches`() =
        runTest {
            val cacheConfig = CacheConfig(
                enabled = true,
                cacheDirectory = "shared_volume",
                maxCacheSize = 4L * 1024 * 1024,
                cacheTtl = 45 * 60 * 1000
            )

            val factoryStorage = CacheStorageFactory.create(
                config = cacheConfig,
                fileSystem = fakeFileSystem,
                clock = clock::now,
                cacheDirectoryProvider = provider
            )
            val manualStorage = equivalentManualStorage(cacheConfig)

            val url = Url("https://example.com/sync-check-reverse")
            val data = TestDataFactory.createCachedResponse(url = url.toString(), body = "payload-b")

            manualStorage.store(url, data)

            val retrieved = factoryStorage.find(url, emptyMap())
            assertNotNull(retrieved)
            assertEquals("payload-b", retrieved.body.decodeToString())
        }
}
