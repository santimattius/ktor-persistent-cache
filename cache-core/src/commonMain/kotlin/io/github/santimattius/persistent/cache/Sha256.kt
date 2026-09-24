package io.github.santimattius.persistent.cache

/**
 * Pure-Kotlin FIPS 180-4 SHA-256 implementation with zero third-party dependencies.
 *
 * `cache-core` must not depend on Okio or kotlinx-io, so this replaces the previous
 * `okio.Buffer().sha256()` usage. It is pinned by public NIST/RFC 6234 test vectors
 * (see `Sha256Test`) and cross-checked against Okio's output for real cache keys in
 * a backend module's test suite, so on-disk cache entries remain addressable after
 * upgrading.
 */
internal object Sha256 {

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    // Round constants: the first 32 bits of the fractional parts of the cube roots
    // of the first 64 primes (2..311). Expressed as Long literals and truncated to
    // Int to avoid any ambiguity with Kotlin's signed 32-bit Int literal range.
    private val K: IntArray = longArrayOf(
        0x428a2f98L, 0x71374491L, 0xb5c0fbcfL, 0xe9b5dba5L,
        0x3956c25bL, 0x59f111f1L, 0x923f82a4L, 0xab1c5ed5L,
        0xd807aa98L, 0x12835b01L, 0x243185beL, 0x550c7dc3L,
        0x72be5d74L, 0x80deb1feL, 0x9bdc06a7L, 0xc19bf174L,
        0xe49b69c1L, 0xefbe4786L, 0x0fc19dc6L, 0x240ca1ccL,
        0x2de92c6fL, 0x4a7484aaL, 0x5cb0a9dcL, 0x76f988daL,
        0x983e5152L, 0xa831c66dL, 0xb00327c8L, 0xbf597fc7L,
        0xc6e00bf3L, 0xd5a79147L, 0x06ca6351L, 0x14292967L,
        0x27b70a85L, 0x2e1b2138L, 0x4d2c6dfcL, 0x53380d13L,
        0x650a7354L, 0x766a0abbL, 0x81c2c92eL, 0x92722c85L,
        0xa2bfe8a1L, 0xa81a664bL, 0xc24b8b70L, 0xc76c51a3L,
        0xd192e819L, 0xd6990624L, 0xf40e3585L, 0x106aa070L,
        0x19a4c116L, 0x1e376c08L, 0x2748774cL, 0x34b0bcb5L,
        0x391c0cb3L, 0x4ed8aa4aL, 0x5b9cca4fL, 0x682e6ff3L,
        0x748f82eeL, 0x78a5636fL, 0x84c87814L, 0x8cc70208L,
        0x90befffaL, 0xa4506cebL, 0xbef9a3f7L, 0xc67178f2L
    ).map { it.toInt() }.toIntArray()

    // Initial hash values: the first 32 bits of the fractional parts of the square
    // roots of the first 8 primes (2..19).
    private val H0: IntArray = longArrayOf(
        0x6a09e667L, 0xbb67ae85L, 0x3c6ef372L, 0xa54ff53aL,
        0x510e527fL, 0x9b05688cL, 0x1f83d9abL, 0x5be0cd19L
    ).map { it.toInt() }.toIntArray()

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of [input].
     */
    fun hex(input: ByteArray): String {
        val bytes = digest(input)
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xff
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0f]
        }
        return out.concatToString()
    }

    /**
     * Returns the raw 32-byte SHA-256 digest of [input].
     */
    fun digest(input: ByteArray): ByteArray {
        val h = H0.copyOf()
        val bitLength = input.size.toLong() * 8

        // Pad: append 0x80, then zeros, then the 64-bit big-endian message bit-length,
        // so the total length is a multiple of 64 bytes (one 512-bit block).
        val paddedLength = ((input.size + 8) / 64 + 1) * 64
        val padded = ByteArray(paddedLength)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 1 - i] = ((bitLength ushr (8 * i)) and 0xff).toByte()
        }

        val w = IntArray(64)
        var offset = 0
        while (offset < paddedLength) {
            for (t in 0 until 16) {
                val base = offset + t * 4
                w[t] = ((padded[base].toInt() and 0xff) shl 24) or
                    ((padded[base + 1].toInt() and 0xff) shl 16) or
                    ((padded[base + 2].toInt() and 0xff) shl 8) or
                    (padded[base + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = rightRotate(w[t - 15], 7) xor rightRotate(w[t - 15], 18) xor (w[t - 15] ushr 3)
                val s1 = rightRotate(w[t - 2], 17) xor rightRotate(w[t - 2], 19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (t in 0 until 64) {
                val s1 = rightRotate(e, 6) xor rightRotate(e, 11) xor rightRotate(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[t] + w[t]
                val s0 = rightRotate(a, 2) xor rightRotate(a, 13) xor rightRotate(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh

            offset += 64
        }

        val result = ByteArray(32)
        for (i in 0 until 8) {
            result[i * 4] = (h[i] ushr 24).toByte()
            result[i * 4 + 1] = (h[i] ushr 16).toByte()
            result[i * 4 + 2] = (h[i] ushr 8).toByte()
            result[i * 4 + 3] = h[i].toByte()
        }
        return result
    }

    private fun rightRotate(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))
}
