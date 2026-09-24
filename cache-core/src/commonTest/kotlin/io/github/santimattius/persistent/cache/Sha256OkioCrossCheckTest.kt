package io.github.santimattius.persistent.cache

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks [Sha256] against Okio's `Buffer().sha256()` for real cache keys
 * (URLs used across the existing behavior test suite), so pre-existing on-disk
 * cache entries remain addressable after the split to a pure-Kotlin digest.
 *
 * DEVIATION from tasks.md 1.5: the task names `shared/commonTest` as the temporary
 * home for this test. That is not possible: [Sha256] is `internal` to `cache-core`
 * (design decision #1, Engram #1505) and Kotlin's `internal` visibility does not
 * cross a Gradle module boundary even with a dependency edge — `:shared` cannot see
 * it regardless of any test-only dependency added on `:cache-core`. This test lives
 * here instead, with Okio added only to `cache-core`'s `commonTest` (never
 * `commonMain`), so `cache-core`'s zero-I/O-dependency production surface is
 * unaffected. Phase 2 (tasks.md 2.3) moves this test to `cache-okio/commonTest`,
 * where Okio becomes a normal backend dependency.
 */
class Sha256OkioCrossCheckTest {

    private val realCacheKeyUrls = listOf(
        "https://api.example.com/data",
        "https://api.example.com/data/0",
        "https://api.example.com/data1",
        "https://api.example.com/data2",
        "https://api.example.com/data3",
        "https://api.example.com/users",
        "https://api.example.com/posts",
        "https://api.example.com/content",
        "https://api.example.com/nonexistent",
        "https://api.example.com/for-remove",
        "https://api.example.com/for-removeAll",
        "https://example.com/api/data"
    )

    @Test
    fun `given real cache key urls when hashed then pure-Kotlin digest matches Okio digest`() {
        for (url in realCacheKeyUrls) {
            val bytes = url.encodeToByteArray()

            val pureKotlinHex = Sha256.hex(bytes)
            val okioHex = Buffer().write(bytes).sha256().hex()

            assertEquals(
                okioHex,
                pureKotlinHex,
                "Digest mismatch for \"$url\": pure-Kotlin and Okio must produce identical hex digests " +
                    "so pre-existing on-disk cache entries remain addressable after upgrade."
            )
        }
    }
}
