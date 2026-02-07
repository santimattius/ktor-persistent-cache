package io.github.santimattius.persistent.cache

import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.serialization.Serializable

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

@Serializable
internal data class HttpStatusCodeCopy(val value: Int, val description: String)

@Serializable
internal data class HttpProtocolVersionCopy(val name: String, val major: Int, val minor: Int)