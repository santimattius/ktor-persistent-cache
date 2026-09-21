package io.github.santimattius.persistent.cache

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NIST/RFC 6234 test vectors for the pure-Kotlin SHA-256 implementation.
 *
 * These vectors pin the digest bytes independently of any third-party hashing
 * library, so [Sha256] can be verified without a dependency on Okio.
 */
class Sha256Test {

    @Test
    fun `given empty string when hashed then matches NIST vector`() {
        val result = Sha256.hex("".encodeToByteArray())

        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result
        )
    }

    @Test
    fun `given short message abc when hashed then matches NIST vector`() {
        val result = Sha256.hex("abc".encodeToByteArray())

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result
        )
    }

    @Test
    fun `given two-block message when hashed then matches NIST vector`() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"

        val result = Sha256.hex(input.encodeToByteArray())

        // NOTE: the spec artifact (Engram #1504) lists this vector as 63 hex chars
        // ("...419db06c"), missing the trailing "1". A SHA-256 digest is always
        // exactly 64 hex chars (32 bytes); the correct RFC 6234 vector for this
        // input ends in "...419db06c1", verified independently below.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            result
        )
    }
}
