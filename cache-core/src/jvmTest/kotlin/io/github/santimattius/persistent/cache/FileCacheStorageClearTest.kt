@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem
import io.ktor.http.Url
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class FileCacheStorageClearTest {

    @Test
    fun `clear is serialized with store`() = runBlocking {
        val enteredDelete = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)
        val countWrites = AtomicBoolean(false)
        val writesAfterSetup = AtomicInteger(0)
        val delegate = InMemoryCacheFileSystem()
        val fileSystem = object : CacheFileSystem<String> by delegate {
            override fun write(path: String, bytes: ByteArray) {
                if (countWrites.get()) writesAfterSetup.incrementAndGet()
                delegate.write(path, bytes)
            }

            override fun delete(path: String) {
                enteredDelete.countDown()
                assertTrue(releaseDelete.await(5, TimeUnit.SECONDS), "clear latch timed out")
                delegate.delete(path)
            }
        }
        val storage = FileCacheStorage(fileSystem, "/root", "cache", maxSize = 0, ttl = 0)
        val firstUrl = Url("https://example.test/first")
        val secondUrl = Url("https://example.test/second")
        storage.store(firstUrl, TestDataFactory.createCachedResponse(firstUrl.toString()))
        countWrites.set(true)

        val clearing = async(Dispatchers.Default) { storage.clear() }
        assertTrue(enteredDelete.await(5, TimeUnit.SECONDS), "clear never reached delete")
        val storing = async(start = CoroutineStart.UNDISPATCHED) {
            storage.store(secondUrl, TestDataFactory.createCachedResponse(secondUrl.toString()))
        }

        assertEquals(0, writesAfterSetup.get(), "store overlapped a blocked clear")
        releaseDelete.countDown()
        clearing.await()
        storing.await()

        assertEquals(1, writesAfterSetup.get())
        assertFalse(storage.find(secondUrl, emptyMap()) == null)
    }

    @Test
    fun `clear preserves cancellation and wraps ordinary failures`() = runBlocking {
        val cancellation = CancellationException("cancel-clear")
        val cancellingFileSystem = object : CacheFileSystem<String> by InMemoryCacheFileSystem() {
            override fun list(dir: String): List<String> = throw cancellation
        }
        val cancelledStorage = FileCacheStorage(cancellingFileSystem, "/root", "cache", 0, 0)

        val thrownCancellation = assertFailsWith<CancellationException> { cancelledStorage.clear() }

        assertSame(cancellation, thrownCancellation)

        val failure = IllegalStateException("cannot-list")
        val failingFileSystem = object : CacheFileSystem<String> by InMemoryCacheFileSystem() {
            override fun list(dir: String): List<String> = throw failure
        }
        val failingStorage = FileCacheStorage(failingFileSystem, "/root", "cache", 0, 0)

        val thrownFailure = assertFailsWith<CacheStorageException> { failingStorage.clear() }

        assertEquals("Failed to clear cache entries", thrownFailure.message)
        assertSame(failure, thrownFailure.cause)
    }
}
