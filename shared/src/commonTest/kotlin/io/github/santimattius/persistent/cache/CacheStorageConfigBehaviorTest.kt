package io.github.santimattius.persistent.cache

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for [CacheStorageConfig] and its relationship with [CacheConfig].
 */
class CacheStorageConfigBehaviorTest {

    @Test
    fun `given CacheStorageConfig default with custom values then properties match`() {
        val minSize = CacheStorageConfig.default(
            cacheDirectory = "dir_a",
            maxCacheSize = 512L,
            cacheTtl = 12345L
        )

        assertEquals("dir_a", minSize.cacheDirectory)
        assertEquals(512L, minSize.maxCacheSize)
        assertEquals(12345L, minSize.cacheTtl)
    }

    @Test
    fun `given CacheStorageConfig default with no overrides then builtin defaults apply`() {
        val defaulted = CacheStorageConfig.default()

        assertEquals("http_cache", defaulted.cacheDirectory)
        assertEquals(10L * 1024 * 1024, defaulted.maxCacheSize)
        assertEquals(60 * 60 * 1000L, defaulted.cacheTtl)
    }

    @Test
    fun `given CacheConfig when paired with CacheStorageConfig default having same params then shapes match`() {
        val cacheConfig = CacheConfig(
            enabled = false,
            cacheDirectory = "ktor_cache_bundle",
            maxCacheSize = 3L * 1024,
            cacheTtl = 90_000L
        )
        val standalone = CacheStorageConfig.default(
            cacheDirectory = cacheConfig.cacheDirectory,
            maxCacheSize = cacheConfig.maxCacheSize,
            cacheTtl = cacheConfig.cacheTtl
        )

        assertEquals(storageShape(cacheConfig), storageShape(standalone))
    }

    private fun storageShape(config: CacheStorageConfig) =
        Triple(config.cacheDirectory, config.maxCacheSize, config.cacheTtl)
}
