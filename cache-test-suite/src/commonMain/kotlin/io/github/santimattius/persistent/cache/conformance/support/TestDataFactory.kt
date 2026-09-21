package io.github.santimattius.persistent.cache.conformance.support

import io.ktor.client.plugins.cache.storage.CachedResponseData
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate

/**
 * Factory for creating test data objects used across conformance suites.
 */
object TestDataFactory {

    /**
     * Creates a CachedResponseData for testing purposes.
     */
    fun createCachedResponse(
        url: String = "https://example.com/api/data",
        body: String = "test response body",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expiresInMillis: Long = 3600000 // 1 hour
    ): CachedResponseData {
        val now = GMTDate()
        return CachedResponseData(
            url = Url(url),
            statusCode = statusCode,
            requestTime = now,
            responseTime = now,
            version = HttpProtocolVersion.HTTP_1_1,
            expires = GMTDate(now.timestamp + expiresInMillis),
            headers = headersOf("Content-Type" to listOf("application/json")),
            varyKeys = emptyMap(),
            body = body.encodeToByteArray()
        )
    }

    /**
     * Creates a CachedResponseData with specific vary keys.
     */
    fun createCachedResponseWithVaryKeys(
        url: String = "https://example.com/api/data",
        body: String = "test response body",
        varyKeys: Map<String, String>
    ): CachedResponseData {
        val now = GMTDate()
        return CachedResponseData(
            url = Url(url),
            statusCode = HttpStatusCode.OK,
            requestTime = now,
            responseTime = now,
            version = HttpProtocolVersion.HTTP_1_1,
            expires = GMTDate(now.timestamp + 3600000),
            headers = headersOf("Content-Type" to listOf("application/json")),
            varyKeys = varyKeys,
            body = body.encodeToByteArray()
        )
    }
}
