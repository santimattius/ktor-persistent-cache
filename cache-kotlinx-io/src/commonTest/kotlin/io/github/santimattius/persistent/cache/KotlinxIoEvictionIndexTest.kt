@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.EvictionIndexConformanceTest
import kotlin.test.AfterTest

/** Verifies [EvictionIndexConformanceTest] against [KotlinxIoCacheFileSystem], on a real temp dir. */
class KotlinxIoEvictionIndexTest : EvictionIndexConformanceTest<kotlinx.io.files.Path>() {
    private val tempRoot = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-eviction-index")
    override val root: String get() = tempRoot
    override fun createFileSystem(): CacheFileSystem<kotlinx.io.files.Path> = KotlinxIoCacheFileSystem()

    @AfterTest
    fun cleanupTempDirectory() = KotlinxIoConformanceTestSupport.deleteRecursively(tempRoot)
}
