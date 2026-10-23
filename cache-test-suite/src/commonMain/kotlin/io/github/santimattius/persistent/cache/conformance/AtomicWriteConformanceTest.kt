package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.CacheStorageException
import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.CrashingCacheFileSystem
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for crash-safe writes: entries are written to a temporary file and moved
 * onto the target, so a crash never leaves a readable partial entry.
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class AtomicWriteConformanceTest<P> : CacheConformanceSupport<P>() {

    private fun tmpNames(): List<String> =
        fileSystem.list(cacheDir()).map { fileSystem.name(it) }.filter { it.endsWith(".tmp") }

    @Test
    fun `given a file system when writeAtomically is called twice then the target holds the latest bytes and no tmp file remains`() {
        // Given
        fileSystem.createDirectories(cacheDir())
        val target = cacheFilePath("entry.cache")

        // When
        fileSystem.writeAtomically(target, byteArrayOf(1, 2, 3))
        fileSystem.writeAtomically(target, byteArrayOf(4, 5))

        // Then
        assertContentEquals(byteArrayOf(4, 5), fileSystem.read(target))
        assertEquals(listOf("entry.cache"), fileSystem.list(cacheDir()).map { fileSystem.name(it) })
    }

    @Test
    fun `given a crash between tmp write and move when storing over an entry then the previous entry stays readable`() =
        runTest {
            // Given - an existing, healthy entry
            val crashing = CrashingCacheFileSystem(fileSystem)
            val storage = newStorage(backend = crashing)
            val url = Url("https://api.example.com/data")
            storage.store(url, TestDataFactory.createCachedResponse(url.toString(), body = "old"))
            val entryName = fileSystem.name(cacheFiles().single())

            // When - the next write dies after the tmp file is written
            crashing.crashTmpPath = cacheFilePath("$entryName.tmp")
            assertFailsWith<CacheStorageException> {
                storage.store(url, TestDataFactory.createCachedResponse(url.toString(), body = "new"))
            }
            crashing.crashTmpPath = null

            // Then - the old entry is intact and the partial file is not an entry
            val found = assertNotNull(storage.find(url, emptyMap()))
            assertEquals("old", found.body.decodeToString())
            assertEquals(1, cacheFiles().size)
        }

    @Test
    fun `given a crash while storing a new entry then no readable entry exists`() = runTest {
        // Given
        val crashing = CrashingCacheFileSystem(fileSystem)
        val storage = newStorage(backend = crashing)
        val url = Url("https://api.example.com/data")
        crashing.crashTmpPath = cacheFilePath("partial.cache.tmp")

        // When
        assertFailsWith<CacheStorageException> {
            storage.store(url, TestDataFactory.createCachedResponse(url.toString()))
        }

        // Then
        assertEquals(null, storage.find(url, emptyMap()))
        assertTrue(storage.findAll(url).isEmpty())
        assertTrue(cacheFiles().isEmpty())
    }

    @Test
    fun `given stale tmp files when a storage starts then they are deleted`() {
        // Given
        fileSystem.createDirectories(cacheDir())
        fileSystem.write(cacheFilePath("stale.cache.tmp"), byteArrayOf(1))

        // When
        newStorage()

        // Then
        assertTrue(tmpNames().isEmpty(), "stale tmp files must be removed at startup")
    }

    @Test
    fun `given stale tmp files when clear is called then they are deleted`() = runTest {
        // Given
        val storage = newStorage()
        fileSystem.write(cacheFilePath("stale.cache.tmp"), byteArrayOf(1))

        // When
        storage.clear()

        // Then
        assertTrue(tmpNames().isEmpty(), "clear() must remove tmp files")
    }

    @Test
    fun `given a body of several MiB when stored then it round-trips`() = runTest {
        // Given
        val storage = newStorage(maxSize = 0)
        val url = Url("https://api.example.com/large")
        val body = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }
        val response = TestDataFactory.createCachedResponse(url.toString()).let {
            io.ktor.client.plugins.cache.storage.CachedResponseData(
                it.url, it.statusCode, it.requestTime, it.responseTime, it.version,
                it.expires, it.headers, it.varyKeys, body
            )
        }

        // When
        storage.store(url, response)

        // Then
        assertContentEquals(body, assertNotNull(storage.find(url, emptyMap())).body)
    }
}
