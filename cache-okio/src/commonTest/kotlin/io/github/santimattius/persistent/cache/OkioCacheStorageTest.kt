package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.support.TestClock
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.fakefilesystem.FakeFileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies the public [OkioCacheStorage] factory, used directly with Ktor's `HttpCache`. */
class OkioCacheStorageTest {

    private val realFileSystem = FileSystem.SYSTEM
    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor-okio-storage-${Random.nextLong()}"
        realFileSystem.createDirectories(root)
    }

    @AfterTest
    fun tearDown() {
        realFileSystem.deleteRecursively(root, mustExist = false)
    }

    private fun provider(path: Path = root) = object : CacheDirectoryProvider {
        override val cacheDirectory: String = path.toString()
    }

    private fun countingEngine(onRequest: () -> Unit) = MockEngine {
        onRequest()
        respond(
            content = "payload",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.CacheControl, "max-age=3600")
        )
    }

    private fun client(engine: MockEngine, directory: String = "http_cache") = HttpClient(engine) {
        install(HttpCache) {
            publicStorage(OkioCacheStorage(directory = directory, directoryProvider = provider()))
        }
    }

    @Test
    fun `given a cached response when a second client reads the same directory then the engine is not called`() =
        runTest {
            var requests = 0
            val engine = countingEngine { requests++ }
            val url = "https://api.example.com/okio-factory"

            val first = client(engine)
            try {
                assertEquals("payload", first.get(url).bodyAsText())
            } finally {
                first.close()
            }
            assertEquals(1, requests)

            val second = client(engine)
            try {
                assertEquals("payload", second.get(url).bodyAsText())
            } finally {
                second.close()
            }
            assertEquals(1, requests, "Second client must be served from disk with zero engine requests")
        }

    @Test
    fun `given default parameters then entries land in the http_cache directory`() = runTest {
        val storage = OkioCacheStorage(directoryProvider = provider())
        val url = Url("https://api.example.com/defaults")

        storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

        val files = realFileSystem.list(root / "http_cache")
        assertTrue(files.any { it.name.endsWith(".cache") }, "Expected a .cache file under http_cache")
    }

    @Test
    fun `given a custom directory then entries land in that directory`() = runTest {
        val storage = OkioCacheStorage(directory = "custom_dir", directoryProvider = provider())
        val url = Url("https://api.example.com/custom")

        storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))

        assertTrue(realFileSystem.exists(root / "custom_dir"))
        assertTrue(!realFileSystem.exists(root / "http_cache"))
    }

    @Test
    fun `given a ttl when the clock passes it then the entry is expired`() = runTest {
        val clock = TestClock()
        val storage = OkioCacheStorage(
            ttl = 500,
            directoryProvider = provider(),
            fileSystem = FakeFileSystem(),
            clock = clock::now
        )
        val url = Url("https://api.example.com/ttl")
        storage.store(url, TestDataFactory.createCachedResponse(url = url.toString()))
        assertNotNull(storage.find(url, emptyMap()))

        clock.advance(501)

        assertNull(storage.find(url, emptyMap()))
    }

    @Test
    fun `given a maxSize when more data is stored then older entries are evicted`() = runTest {
        val clock = TestClock()
        val storage = OkioCacheStorage(
            maxSize = 600,
            directoryProvider = provider(),
            clock = clock::now
        )
        val urls = (0 until 10).map { Url("https://api.example.com/size/$it") }
        urls.forEach {
            clock.advance(10)
            storage.store(it, TestDataFactory.createCachedResponse(url = it.toString(), body = "x".repeat(200)))
        }

        val total = realFileSystem.list(root / "http_cache")
            .filter { it.name.endsWith(".cache") }
            .sumOf { realFileSystem.metadata(it).size ?: 0L }
        assertTrue(total <= 600, "On-disk size $total must not exceed maxSize")
        assertNull(storage.find(urls.first(), emptyMap()), "Oldest entry must have been evicted")
        assertNotNull(storage.find(urls.last(), emptyMap()), "Newest entry must remain")
    }
}
