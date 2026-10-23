package io.github.santimattius.persistent.cache.sample

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentCacheSampleTest {
    @Test
    fun `verifyAll reports an offline persisted cache hit for each backend`() =
        runTest {
            val rootDirectory = createSampleTestDirectory()
            try {
                val results = PersistentCacheSample(rootDirectory).verifyAll()

                assertEquals(listOf("Okio", "kotlinx-io"), results.map { it.backend })
                results.forEach { result ->
                    assertEquals(1, result.firstClientEngineRequests)
                    assertEquals(0, result.secondClientEngineRequests)
                    assertTrue(result.secondClientReadPersistedData)
                    assertEquals("sample response from ${result.backend}", result.responseBody)
                }
            } finally {
                deleteSampleTestDirectory(rootDirectory)
            }
        }
}

internal expect fun createSampleTestDirectory(): String

internal expect fun deleteSampleTestDirectory(rootDirectory: String)
