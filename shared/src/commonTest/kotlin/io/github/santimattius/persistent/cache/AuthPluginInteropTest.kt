package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.doubles.FakeCacheDirectoryProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Auth + persistent-cache coexistence tests per spec (issue #25, domain `ktor-auth-plugin-interop`).
 *
 * Exercises an [HttpClient] with both [Auth] (Bearer refresh on 401) and [installPersistentCache]
 * installed together. Persistence is verified by opening a second client against the same on-disk
 * cache directory: a cache hit must not invoke [MockEngine] again.
 *
 * Ktor routing note (documented here and in README task 5.4): authenticated cacheable responses
 * must use `Cache-Control: private` to land in [HttpCache.Config.privateStorage] (our Okio-backed
 * storage). Responses with only `max-age` route to public storage, which [installPersistentCache]
 * does not configure when [CacheConfig.isPublic] is false. Also, [CacheConfig.isShared] must be
 * false so Ktor does not skip cache lookup/storage for authorized requests on a "shared" client.
 */
class AuthPluginInteropTest {

    private lateinit var cacheRootDir: Path

    @BeforeTest
    fun setUp() {
        val uniqueSuffix = Random.nextLong().toString()
        cacheRootDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ktor-cache-auth-interop-test-$uniqueSuffix"
        FileSystem.SYSTEM.createDirectories(cacheRootDir)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(cacheRootDir, mustExist = false)
    }

    private fun cacheConfig() = CacheConfig(
        enabled = true,
        cacheDirectory = "http_cache",
        maxCacheSize = 10L * 1024 * 1024,
        cacheTtl = 60 * 60 * 1000,
        // Unshared client: Ktor skips cache for Authorization when isShared=true (HttpCache.kt:163)
        // and refuses to store private entries when isShared=true (HttpCache.kt:304).
        isShared = false,
        isPublic = false
    )

    private fun cacheDirectoryProvider() = FakeCacheDirectoryProvider(cacheRootDir.toString())

    private fun authMockEngine(
        validToken: String,
        cacheControl: String,
        body: String,
        onRequest: () -> Unit = {}
    ): MockEngine =
        MockEngine { request ->
            onRequest()
            val authHeader = request.headers[HttpHeaders.Authorization]
            if (authHeader != "Bearer $validToken") {
                respond(
                    content = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
                )
            } else {
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.CacheControl to listOf(cacheControl),
                        HttpHeaders.ContentType to listOf("text/plain")
                    )
                )
            }
        }

    private fun buildClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("expired-access-token", "refresh-token") }
                    refreshTokens { BearerTokens(VALID_TOKEN, "refresh-token") }
                }
            }
            installPersistentCache(cacheConfig(), cacheDirectoryProvider())
        }

    @Test
    fun `given Auth and persistent cache when cacheable authenticated response succeeds then it is persisted`() =
        runTest {
            val url = "https://api.example.com/auth-cacheable"
            val cacheableBody = "authenticated cacheable payload"
            var mockInvocations = 0

            val engine = authMockEngine(
                validToken = VALID_TOKEN,
                // private + max-age routes the entry to privateStorage (Okio), per RFC 7234
                cacheControl = "private, max-age=3600",
                body = cacheableBody,
                onRequest = { mockInvocations++ }
            )

            val clientA = buildClient(engine)
            try {
                val response = clientA.get(url)
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(cacheableBody, response.bodyAsText())
                // 401 (expired token) + 200 (after bearer refresh)
                assertEquals(2, mockInvocations)
            } finally {
                clientA.close()
            }

            val persistedEntries = CacheStorageFactory.create(
                config = cacheConfig(),
                fileSystem = FileSystem.SYSTEM,
                cacheDirectoryProvider = cacheDirectoryProvider()
            ).findAll(Url(url))
            assertNotNull(
                persistedEntries.firstOrNull(),
                "Cacheable authenticated response with Cache-Control private must be persisted to Okio storage"
            )

            mockInvocations = 0
            val clientB = buildClient(engine)
            try {
                val cachedResponse = clientB.get(url)
                assertEquals(HttpStatusCode.OK, cachedResponse.status)
                assertEquals(cacheableBody, cachedResponse.bodyAsText())
                assertEquals(
                    0,
                    mockInvocations,
                    "Second client must serve the cacheable response from on-disk cache without hitting MockEngine"
                )
            } finally {
                clientB.close()
            }
        }

    @Test
    fun `given Auth and persistent cache when no-store authenticated response succeeds then it is not persisted`() =
        runTest {
            val url = "https://api.example.com/auth-no-store"
            val noStoreBody = "authenticated no-store payload"
            var mockInvocations = 0

            val engine = authMockEngine(
                validToken = VALID_TOKEN,
                cacheControl = "no-store",
                body = noStoreBody,
                onRequest = { mockInvocations++ }
            )

            val clientA = buildClient(engine)
            try {
                val response = clientA.get(url)
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(noStoreBody, response.bodyAsText())
                assertEquals(2, mockInvocations)
            } finally {
                clientA.close()
            }

            val persistedEntries = CacheStorageFactory.create(
                config = cacheConfig(),
                fileSystem = FileSystem.SYSTEM,
                cacheDirectoryProvider = cacheDirectoryProvider()
            ).findAll(Url(url))
            assertTrue(persistedEntries.isEmpty(), "no-store response must not be persisted")

            mockInvocations = 0
            val clientB = buildClient(engine)
            try {
                val secondResponse = clientB.get(url)
                assertEquals(HttpStatusCode.OK, secondResponse.status)
                assertEquals(noStoreBody, secondResponse.bodyAsText())
                assertTrue(
                    mockInvocations >= 2,
                    "no-store response must not be cached; second client must re-fetch via MockEngine (401 + retry)"
                )
            } finally {
                clientB.close()
            }
        }

    private companion object {
        const val VALID_TOKEN = "refreshed-access-token"
    }
}
