@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.InfiniteTtlConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [InfiniteTtlConformanceTest] against [OkioCacheFileSystem]. */
class OkioInfiniteTtlTest : InfiniteTtlConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
