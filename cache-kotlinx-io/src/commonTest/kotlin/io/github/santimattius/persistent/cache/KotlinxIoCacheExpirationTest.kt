@file:OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.CacheExpirationConformanceTest
import kotlin.test.AfterTest

/** Verifies [CacheExpirationConformanceTest] against [KotlinxIoCacheFileSystem], on a real temp dir. */
class KotlinxIoCacheExpirationTest : CacheExpirationConformanceTest<kotlinx.io.files.Path>() {
    private val tempRoot = KotlinxIoConformanceTestSupport.freshTempDirectory("ktor-cache-expiration")
    override val root: String get() = tempRoot
    override fun createFileSystem(): CacheFileSystem<kotlinx.io.files.Path> = KotlinxIoCacheFileSystem()

    @AfterTest
    fun cleanupTempDirectory() = KotlinxIoConformanceTestSupport.deleteRecursively(tempRoot)
}
