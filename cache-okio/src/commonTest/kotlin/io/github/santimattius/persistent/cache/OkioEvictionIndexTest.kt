@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.EvictionIndexConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [EvictionIndexConformanceTest] against [OkioCacheFileSystem]. */
class OkioEvictionIndexTest : EvictionIndexConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
