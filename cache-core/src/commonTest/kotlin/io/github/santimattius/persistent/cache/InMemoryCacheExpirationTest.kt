@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.CacheExpirationConformanceTest
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem

/** Verifies [CacheExpirationConformanceTest] against the reference [InMemoryCacheFileSystem]. */
class InMemoryCacheExpirationTest : CacheExpirationConformanceTest<String>() {
    override fun createFileSystem(): CacheFileSystem<String> = InMemoryCacheFileSystem()
}
