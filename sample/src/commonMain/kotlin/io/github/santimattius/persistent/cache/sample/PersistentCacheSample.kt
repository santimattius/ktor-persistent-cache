package io.github.santimattius.persistent.cache.sample

import io.github.santimattius.persistent.cache.CacheDirectoryProvider
import io.github.santimattius.persistent.cache.CacheFileSystem
import io.github.santimattius.persistent.cache.ExperimentalKotlinxIoCache
import io.github.santimattius.persistent.cache.FileCacheStorage
import io.github.santimattius.persistent.cache.InternalPersistentCacheApi
import io.github.santimattius.persistent.cache.KotlinxIoCacheFileSystem
import io.github.santimattius.persistent.cache.OkioCacheFileSystem
import io.github.santimattius.persistent.cache.PersistentCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * A Swift-friendly result from one offline persistence scenario.
 *
 * [secondClientReadPersistedData] is true only when a fresh client returns the response body
 * without invoking the MockEngine after the first client has persisted it.
 */
public data class PersistenceCheckResult(
    public val backend: String,
    public val firstClientEngineRequests: Int,
    public val secondClientEngineRequests: Int,
    public val secondClientReadPersistedData: Boolean,
    public val responseBody: String,
)

/**
 * Runs deterministic, offline persistence checks for the Okio and kotlinx-io backends.
 *
 * The caller supplies a platform cache root as a plain [String]. This keeps the API usable from
 * Swift and Android callers without exposing either backend's path type.
 */
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
public class PersistentCacheSample(
    private val rootDirectory: String,
) {
    /** Runs both filesystem checks in separate directories beneath [rootDirectory]. */
    public suspend fun verifyAll(): List<PersistenceCheckResult> =
        listOf(
            verifyOkio(),
            verifyKotlinxIo(),
        )

    /** Runs the persistence scenario against the real Okio system filesystem adapter. */
    public suspend fun verifyOkio(): PersistenceCheckResult =
        runPersistenceCheck(
            backend = OKIO_BACKEND,
            cacheDirectory = "kmp-backend-sample-okio",
            fileSystem = OkioCacheFileSystem(),
        )

    /** Runs the persistence scenario against the real kotlinx-io system filesystem adapter. */
    public suspend fun verifyKotlinxIo(): PersistenceCheckResult =
        runPersistenceCheck(
            backend = KOTLINX_IO_BACKEND,
            cacheDirectory = "kmp-backend-sample-kotlinx-io",
            fileSystem = KotlinxIoCacheFileSystem(),
        )

    private suspend fun <P> runPersistenceCheck(
        backend: String,
        cacheDirectory: String,
        fileSystem: CacheFileSystem<P>,
    ): PersistenceCheckResult {
        val cacheStorage =
            FileCacheStorage(
                fileSystem = fileSystem,
                directoryRoot = rootDirectory,
                directoryName = cacheDirectory,
                maxSize = MAX_CACHE_SIZE,
                ttl = CACHE_TTL,
            )
        cacheStorage.clear()

        try {
            var engineRequests = 0
            val engine =
                MockEngine {
                    engineRequests += 1
                    respond(
                        content = "sample response from $backend",
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.CacheControl to listOf("private, max-age=3600"),
                                HttpHeaders.ContentType to listOf("text/plain"),
                            ),
                    )
                }

            val firstClient = createClient(engine, fileSystem, cacheDirectory)
            val firstBody =
                try {
                    firstClient.get(SAMPLE_URL).bodyAsText()
                } finally {
                    firstClient.close()
                }
            check(firstBody == "sample response from $backend")
            val firstClientEngineRequests = engineRequests

            engineRequests = 0
            val secondClient = createClient(engine, fileSystem, cacheDirectory)
            val secondBody =
                try {
                    secondClient.get(SAMPLE_URL).bodyAsText()
                } finally {
                    secondClient.close()
                }
            val secondClientEngineRequests = engineRequests

            return PersistenceCheckResult(
                backend = backend,
                firstClientEngineRequests = firstClientEngineRequests,
                secondClientEngineRequests = secondClientEngineRequests,
                secondClientReadPersistedData = secondBody == firstBody && secondClientEngineRequests == 0,
                responseBody = secondBody,
            )
        } finally {
            cacheStorage.clear()
        }
    }

    private fun <P> createClient(
        engine: MockEngine,
        fileSystem: CacheFileSystem<P>,
        cacheDirectory: String,
    ): HttpClient =
        HttpClient(engine) {
            install(PersistentCache) {
                directory = cacheDirectory
                maxSize = MAX_CACHE_SIZE
                ttl = CACHE_TTL
                shared = false
                public = false
                this.fileSystem = fileSystem
                directoryProvider =
                    object : CacheDirectoryProvider {
                        override val cacheDirectory: String = rootDirectory
                    }
            }
        }

    private companion object {
        const val OKIO_BACKEND = "Okio"
        const val KOTLINX_IO_BACKEND = "kotlinx-io"
        const val SAMPLE_URL = "https://sample.invalid/persistent-cache"
        const val MAX_CACHE_SIZE = 1024L * 1024
        const val CACHE_TTL = 60L * 60 * 1000
    }
}
