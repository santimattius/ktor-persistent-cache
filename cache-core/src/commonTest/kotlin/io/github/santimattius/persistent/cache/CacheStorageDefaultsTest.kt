@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import kotlin.test.Test
import kotlin.test.assertEquals

class CacheStorageDefaultsTest {

    @Test
    fun `given the shared defaults then they match the documented values`() {
        assertEquals("http_cache", CacheStorageDefaults.DIRECTORY)
        assertEquals(10L * 1024 * 1024, CacheStorageDefaults.MAX_SIZE)
        assertEquals(60L * 60 * 1000, CacheStorageDefaults.TTL)
    }

    @Test
    fun `given a fresh PersistentCacheConfig then it uses the shared defaults`() {
        val config = PersistentCacheConfig()

        assertEquals(CacheStorageDefaults.DIRECTORY, config.directory)
        assertEquals(CacheStorageDefaults.MAX_SIZE, config.maxSize)
        assertEquals(CacheStorageDefaults.TTL, config.ttl)
    }
}
