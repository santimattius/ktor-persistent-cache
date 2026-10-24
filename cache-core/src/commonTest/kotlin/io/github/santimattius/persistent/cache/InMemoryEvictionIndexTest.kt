@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.EvictionIndexConformanceTest
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem

/** Verifies [EvictionIndexConformanceTest] against the reference [InMemoryCacheFileSystem]. */
class InMemoryEvictionIndexTest : EvictionIndexConformanceTest<String>() {
    override fun createFileSystem(): CacheFileSystem<String> = InMemoryCacheFileSystem()
}
