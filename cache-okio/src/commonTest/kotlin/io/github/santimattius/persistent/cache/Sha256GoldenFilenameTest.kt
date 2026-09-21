@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DEVIATION from tasks.md 2.3: the task names moving `Sha256OkioCrossCheckTest.kt` (task 1.5)
 * from `cache-core/commonTest` to `cache-okio/commonTest`. That move is impossible for the exact
 * same reason task 1.5 itself could not live in `shared/commonTest` (documented in the Phase 1
 * apply-progress, Engram #1517, Deviation #1): [Sha256] is `internal` to `cache-core`'s
 * `commonMain`, and Kotlin `internal` visibility never crosses a Gradle module boundary, even
 * across a dependency edge and even when the *same* module's `commonTest` (which IS a friend
 * source set) could see it. `cache-okio` is a different module than `cache-core`, so it can no
 * more resolve `Sha256` than `:shared` could. `Sha256OkioCrossCheckTest.kt` therefore STAYS in
 * `cache-core/commonTest`, unmoved, exactly where Phase 1 correctly placed it.
 *
 * What this test adds instead, in the correct module for it: an end-to-end, black-box
 * confirmation that the real [OkioCacheFileSystem] backend produces the exact on-disk filename
 * the pre-split `OkioFileCacheStorage` always produced for a known URL, using a golden SHA-256
 * hex digest computed independently (`python3 -c "import hashlib; ...`", not by calling
 * `Sha256` or `Buffer().sha256()` from this module). This is the practical, module-boundary-safe
 * form of "SHA-256 byte-identity" backward-compatibility coverage that task 2.3 actually needs
 * inside `cache-okio`.
 */
class Sha256GoldenFilenameTest {

    @Test
    fun `given a known url when stored via the okio backend then the on-disk filename matches the pre-split golden hash`() =
        runTest {
            val fileSystem = OkioCacheFileSystem(FakeFileSystem())
            val storage = FileCacheStorage(
                fileSystem = fileSystem,
                directoryRoot = "/root",
                directoryName = "http_cache",
                maxSize = 10L * 1024 * 1024,
                ttl = 60 * 60 * 1000
            )
            val url = Url("https://api.example.com/data")
            val data = CachedResponseData(
                url = url,
                statusCode = HttpStatusCode.OK,
                requestTime = GMTDate(),
                responseTime = GMTDate(),
                version = HttpProtocolVersion.HTTP_1_1,
                expires = GMTDate(GMTDate().timestamp + 3_600_000),
                headers = headersOf("Content-Type" to listOf("text/plain")),
                varyKeys = emptyMap(),
                body = "golden".encodeToByteArray()
            )

            storage.store(url, data)

            val cacheDir = fileSystem.resolve("/root", "http_cache")
            val filenames = fileSystem.list(cacheDir).map { fileSystem.name(it) }

            assertEquals(
                setOf("${GOLDEN_SHA256_HEX_FOR_KNOWN_URL}_0.cache"),
                filenames.toSet(),
                "The Okio backend must keep producing the pre-split filename scheme " +
                    "(SHA-256 hex of the URL + vary-keys hash + \".cache\") so existing on-disk " +
                    "cache entries remain addressable after upgrading."
            )
        }

    private companion object {
        // Computed independently via: python3 -c "import hashlib;
        // print(hashlib.sha256(b'https://api.example.com/data').hexdigest())"
        const val GOLDEN_SHA256_HEX_FOR_KNOWN_URL = "8fc0a700dbdf8cc053b1bda4d226810050204bf8c10a7942c17a820b7f01a111"
    }
}
