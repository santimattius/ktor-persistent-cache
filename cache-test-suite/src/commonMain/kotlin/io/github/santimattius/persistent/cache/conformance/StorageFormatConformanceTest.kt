package io.github.santimattius.persistent.cache.conformance

import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport
import io.github.santimattius.persistent.cache.conformance.support.TestDataFactory
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the on-disk contract: file naming, vary-key collision resistance, the
 * format header and the startup sweep of files written by older versions.
 */
@OptIn(InternalPersistentCacheApi::class)
abstract class StorageFormatConformanceTest<P> : CacheConformanceSupport<P>() {

    private val url = Url("https://api.example.com/data")

    private fun response(body: String, varyKeys: Map<String, String> = emptyMap()) =
        TestDataFactory.createCachedResponseWithVaryKeys(url.toString(), body, varyKeys)

    private fun names(): Set<String> = cacheFiles().map { fileSystem.name(it) }.toSet()

    @Test
    fun `given known url and vary keys when stored then file names match the golden scheme`() = runTest {
        // Given
        val storage = newStorage()

        // When
        storage.store(url, response("none"))
        storage.store(url, response("one", mapOf("Accept-Language" to "en")))
        storage.store(url, response("two", mapOf("Accept-Language" to "en", "Accept-Encoding" to "gzip")))

        // Then - SHA-256 hex of the URL, then 0 or the first 16 hex chars of SHA-256 over the
        // sorted "k=v;k=v" string (digests computed independently with python3 hashlib).
        assertEquals(
            setOf(
                "${URL_HASH}_0.cache",
                "${URL_HASH}_107b310d9d5bd7f5.cache",
                "${URL_HASH}_76d17740dc0b2e89.cache"
            ),
            names()
        )
    }

    @Test
    fun `given vary values with equal String hashCode when stored then they resolve to distinct entries`() =
        runTest {
            // Given - "Aa" and "BB" have the same String.hashCode(), and so do "a=Aa" and "a=BB"
            val storage = newStorage()
            val aa = mapOf("a" to "Aa")
            val bb = mapOf("a" to "BB")

            // When
            storage.store(url, response("first", aa))
            storage.store(url, response("second", bb))

            // Then
            assertEquals(2, cacheFiles().size)
            assertEquals("first", assertNotNull(storage.find(url, aa)).body.decodeToString())
            assertEquals("second", assertNotNull(storage.find(url, bb)).body.decodeToString())
            assertEquals(2, storage.findAll(url).size)
        }

    @Test
    fun `given an entry stored under another vary set when find is called then it is rejected`() = runTest {
        // Given - the bytes of the "en" entry planted at the "es" entry's path
        val storage = newStorage()
        val en = mapOf("Accept-Language" to "en")
        val es = mapOf("Accept-Language" to "es")
        storage.store(url, response("english", en))
        val enFile = cacheFiles().single()
        storage.store(url, response("spanish", es))
        val esFile = cacheFiles().first { it != enFile }
        fileSystem.write(esFile, fileSystem.read(enFile))

        // When / Then
        assertNull(storage.find(url, es))
        assertEquals("english", assertNotNull(storage.find(url, en)).body.decodeToString())
        assertEquals(listOf("english"), storage.findAll(url).map { it.body.decodeToString() })
    }

    @Test
    fun `given a stored entry then the file starts with the magic bytes and format version`() = runTest {
        // Given
        val storage = newStorage()

        // When
        storage.store(url, response("body"))

        // Then
        val bytes = fileSystem.read(cacheFiles().single())
        assertEquals(listOf<Byte>('K'.code.toByte(), 'P'.code.toByte(), 'C'.code.toByte(), 1), bytes.take(4))
    }

    @Test
    fun `given a headerless file when found then it is a miss and the file is deleted`() = runTest {
        // Given
        val storage = newStorage()
        val path = cacheFilePath("${URL_HASH}_0.cache")
        fileSystem.write(path, byteArrayOf(10, 20, 30, 40, 50, 60))

        // When
        val found = storage.find(url, emptyMap())

        // Then
        assertNull(found)
        assertTrue(cacheFiles().isEmpty(), "headerless file must be deleted")
    }

    @Test
    fun `given an unknown format version when found then it is rejected and the file is deleted`() = runTest {
        // Given
        val storage = newStorage()
        storage.store(url, response("body"))
        val file = cacheFiles().single()
        val bytes = fileSystem.read(file)
        bytes[3] = 99
        fileSystem.write(file, bytes)

        // When
        val found = storage.find(url, emptyMap())

        // Then
        assertNull(found)
        assertTrue(cacheFiles().isEmpty(), "unknown-version file must be deleted")
    }

    @Test
    fun `given legacy named files when a storage starts then only well formed names are kept`() = runTest {
        // Given - maxSize 0 so no eviction could ever remove them
        newStorage(maxSize = 0).store(url, response("body"))
        fileSystem.write(cacheFilePath("${URL_HASH}_1a2b3c4d.cache"), byteArrayOf(1))
        fileSystem.write(cacheFilePath("not-a-hash.cache"), byteArrayOf(1))
        val kept = names().filter { it == "${URL_HASH}_0.cache" }
        assertEquals(1, kept.size)

        // When
        val restarted = newStorage(maxSize = 0)

        // Then
        assertEquals(setOf("${URL_HASH}_0.cache"), names())
        assertEquals("body", assertNotNull(restarted.find(url, emptyMap())).body.decodeToString())
    }

    private companion object {
        const val URL_HASH = "8fc0a700dbdf8cc053b1bda4d226810050204bf8c10a7942c17a820b7f01a111"
    }
}
