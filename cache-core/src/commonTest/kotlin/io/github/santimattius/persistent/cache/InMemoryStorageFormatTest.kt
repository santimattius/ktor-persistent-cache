@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.StorageFormatConformanceTest
import io.github.santimattius.persistent.cache.doubles.InMemoryCacheFileSystem

/** Verifies [StorageFormatConformanceTest] against the reference [InMemoryCacheFileSystem]. */
class InMemoryStorageFormatTest : StorageFormatConformanceTest<String>() {
    override fun createFileSystem(): CacheFileSystem<String> = InMemoryCacheFileSystem()
}
