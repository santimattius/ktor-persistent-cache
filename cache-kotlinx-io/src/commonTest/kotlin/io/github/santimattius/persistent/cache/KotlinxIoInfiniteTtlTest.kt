@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.InfiniteTtlConformanceTest
import kotlin.test.AfterTest

/** Verifies [InfiniteTtlConformanceTest] against [KotlinxIoCacheFileSystem], on a real temp dir. */
class KotlinxIoInfiniteTtlTest : InfiniteTtlConformanceTest<kotlinx.io.files.Path>() {
    private val tempRoot = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-infinite-ttl")
    override val root: String get() = tempRoot
    override fun createFileSystem(): CacheFileSystem<kotlinx.io.files.Path> = KotlinxIoCacheFileSystem()

    @AfterTest
    fun cleanupTempDirectory() = KotlinxIoConformanceTestSupport.deleteRecursively(tempRoot)
}
