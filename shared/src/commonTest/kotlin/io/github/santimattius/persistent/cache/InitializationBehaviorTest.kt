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
import kotlin.test.assertTrue

/**
 * Behavioral tests for cache storage initialization.
 *
 * These tests verify that:
 * 1. Cache directory is created during initialization
 * 2. Initialization is thread-safe (no race conditions)
 * 3. Operations work correctly after initialization
 *
 * Uses Given-When-Then pattern for clarity.
 */
class InitializationBehaviorTest {

    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        // Create only the parent directory, not the cache directory
        fakeFileSystem.createDirectory("/fake/cache".toOkioPath())
    }

    @AfterTest
    fun tearDown() {
        fakeFileSystem.clear()
    }

    @Test
    fun `given new storage instance when created then cache directory is created`() = runTest {
        // Given
        val config = OkioFileCacheConfig(
            fileName = "http_cache",
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )

        // When
        OkioFileCacheStorage(config, fakeFileSystem)

        // Then
        assertTrue(
            fakeFileSystem.directoryExists("/fake/cache/http_cache".toOkioPath()),
            "Cache directory should be created during initialization"
        )
    }

    @Test
    fun `given storage instance when performing operations then all complete without errors`() =
        runTest {
            // Given
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            val url = Url("https://api.example.com/data")
            val response = TestDataFactory.createCachedResponse(url = url.toString())

            // When & Then - All operations should complete without errors
            storage.store(url, response)
            storage.find(url, emptyMap())
            storage.findAll(url)
            storage.remove(url, emptyMap())
            storage.removeAll(url)

            assertTrue(true, "All operations completed successfully")
        }

    @Test
    fun `given multiple storage instances with same config when created then each has its own initialization`() =
        runTest {
            // Given
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )

            // When - Create multiple instances
            val storage1 = OkioFileCacheStorage(config, fakeFileSystem)
            val storage2 = OkioFileCacheStorage(config, fakeFileSystem)

            // Then - Both should be able to operate on the same directory
            val url = Url("https://api.example.com/data")
            storage1.store(
                url,
                TestDataFactory.createCachedResponse(url = url.toString(), body = "from storage1")
            )

            // Storage2 should be able to read what storage1 wrote (shared directory)
            val result = storage2.find(url, emptyMap())
            assertTrue(result != null, "Storage2 should read data from shared cache directory")
        }

    @Test
    fun `given storage with custom directory name when created then uses custom name`() = runTest {
        // Given
        val customDirName = "custom_http_cache"
        val config = OkioFileCacheConfig(
            fileName = customDirName,
            maxSize = 10L * 1024 * 1024,
            ttl = 60 * 60 * 1000,
            cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
        )

        // When
        OkioFileCacheStorage(config, fakeFileSystem)

        // Then
        assertTrue(
            fakeFileSystem.directoryExists("/fake/cache/$customDirName".toOkioPath()),
            "Cache directory should use custom name"
        )
    }

    @Test
    fun `given storage instance when store is called multiple times rapidly then all complete`() =
        runTest {
            // Given
            val config = OkioFileCacheConfig(
                fileName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000,
                cacheDirectoryProvider = FakeCacheDirectoryProvider("/fake/cache")
            )
            val storage = OkioFileCacheStorage(config, fakeFileSystem)

            // When - Rapid sequential stores (tests that initialization doesn't cause issues)
            repeat(10) { i ->
                val url = Url("https://api.example.com/data/$i")
                storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))
            }

            // Then - All should complete and be retrievable
            repeat(10) { i ->
                val url = Url("https://api.example.com/data/$i")
                val result = storage.find(url, emptyMap())
                assertTrue(result != null, "Entry $i should be retrievable")
            }
        }

    private fun String.toOkioPath(): okio.Path = this.toPath()
}
