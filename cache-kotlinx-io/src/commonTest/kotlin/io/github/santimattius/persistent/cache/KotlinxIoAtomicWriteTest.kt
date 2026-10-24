@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.AtomicWriteConformanceTest
import kotlin.test.AfterTest

/** Verifies [AtomicWriteConformanceTest] against [KotlinxIoCacheFileSystem], on a real temp dir. */
class KotlinxIoAtomicWriteTest : AtomicWriteConformanceTest<kotlinx.io.files.Path>() {
    private val tempRoot = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-atomic-write")
    override val root: String get() = tempRoot
    override fun createFileSystem(): CacheFileSystem<kotlinx.io.files.Path> = KotlinxIoCacheFileSystem()

    @AfterTest
    fun cleanupTempDirectory() = KotlinxIoConformanceTestSupport.deleteRecursively(tempRoot)
}
