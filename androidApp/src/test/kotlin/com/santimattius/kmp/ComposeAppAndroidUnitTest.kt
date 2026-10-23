package com.santimattius.kmp

import io.github.santimattius.persistent.cache.sample.PersistentCacheSample
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class PersistentCacheSampleAndroidIntegrationTest {
    @Test
    fun bothBackendsPersistDataForANewClient() =
        runBlocking {
            val cacheRoot = Files.createTempDirectory("ktor-persistent-cache-sample")
            try {
                val results = PersistentCacheSample(cacheRoot.toString()).verifyAll()

                assertEquals(setOf("Okio", "kotlinx-io"), results.map { it.backend }.toSet())
                results.forEach { result ->
                    assertEquals(1, result.firstClientEngineRequests)
                    assertEquals(0, result.secondClientEngineRequests)
                    assertTrue(result.secondClientReadPersistedData)
                }
            } finally {
                cacheRoot.deleteRecursively()
            }
        }
}
