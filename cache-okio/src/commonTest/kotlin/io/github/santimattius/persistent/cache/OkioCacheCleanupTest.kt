@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.CacheCleanupConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [CacheCleanupConformanceTest] against [OkioCacheFileSystem]. */
class OkioCacheCleanupTest : CacheCleanupConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
