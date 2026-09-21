@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.RemoveAllConformanceTest
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem

/** Verifies [RemoveAllConformanceTest] against the reference [InMemoryCacheFileSystem]. */
class InMemoryRemoveAllTest : RemoveAllConformanceTest<String>() {
    override fun createFileSystem(): CacheFileSystem<String> = InMemoryCacheFileSystem()
}
