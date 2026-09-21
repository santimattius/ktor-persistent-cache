@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.VaryKeysConformanceTest
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem

/** Verifies [VaryKeysConformanceTest] against the reference [InMemoryCacheFileSystem]. */
class InMemoryVaryKeysTest : VaryKeysConformanceTest<String>() {
    override fun createFileSystem(): CacheFileSystem<String> = InMemoryCacheFileSystem()
}
