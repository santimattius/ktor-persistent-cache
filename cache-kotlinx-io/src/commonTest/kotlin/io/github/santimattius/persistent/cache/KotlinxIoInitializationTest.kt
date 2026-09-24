@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.InitializationConformanceTest
import kotlin.test.AfterTest

/** Verifies [InitializationConformanceTest] against [KotlinxIoCacheFileSystem], on a real temp dir. */
class KotlinxIoInitializationTest : InitializationConformanceTest<kotlinx.io.files.Path>() {
    private val tempRoot = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-initialization")
    override val root: String get() = tempRoot
    override fun createFileSystem(): CacheFileSystem<kotlinx.io.files.Path> = KotlinxIoCacheFileSystem()

    @AfterTest
    fun cleanupTempDirectory() = KotlinxIoConformanceTestSupport.deleteRecursively(tempRoot)
}
