@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.TtlConsistencyConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [TtlConsistencyConformanceTest] against [OkioCacheFileSystem]. */
class OkioTtlConsistencyTest : TtlConsistencyConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
