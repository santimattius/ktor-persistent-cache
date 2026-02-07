package io.github.santimattius.persistent.cache

import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.serialization.Serializable

/**
 * Serializable copy of [io.ktor.client.plugins.cache.storage.CachedResponseData] for persistence.
 *
 * Uses serializable types (e.g. [HttpStatusCodeCopy], [HttpProtocolVersionCopy]) so the response
 * can be encoded/decoded across platforms.
 *
 * @property url The request URL.
 * @property statusCode HTTP status code copy.
 * @property requestTime Time of the original request.
 * @property responseTime Time of the cached response.
 * @property version HTTP protocol version copy.
 * @property expires Expiration time of the cached entry.
 * @property headers Response headers.
 * @property varyKeys Vary keys for content negotiation.
 * @property body Response body bytes.
 */
@Serializable
internal data class CachedResponseDataCopy(
    val url: Url,
    val statusCode: HttpStatusCodeCopy,
    val requestTime: GMTDate,
    val responseTime: GMTDate,
    val version: HttpProtocolVersionCopy,
    val expires: GMTDate,
    val headers: Map<String, List<String>>,
    val varyKeys: Map<String, String>,
    val body: ByteArray
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedResponseDataCopy) return false

        if (url != other.url) return false
        if (varyKeys != other.varyKeys) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + varyKeys.hashCode()
        return result
    }
}

/**
 * Serializable copy of [io.ktor.http.HttpStatusCode] for persistence.
 *
 * @property value HTTP status code value (e.g. 200, 404).
 * @property description Status description (e.g. "OK", "Not Found").
 */
@Serializable
internal data class HttpStatusCodeCopy(val value: Int, val description: String)

/**
 * Serializable copy of [io.ktor.http.HttpProtocolVersion] for persistence.
 *
 * @property name Protocol name (e.g. "HTTP").
 * @property major Major version number.
 * @property minor Minor version number.
 */
@Serializable
internal data class HttpProtocolVersionCopy(val name: String, val major: Int, val minor: Int)