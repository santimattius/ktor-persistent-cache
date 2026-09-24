@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.RemoveAllConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [RemoveAllConformanceTest] against [OkioCacheFileSystem]. */
class OkioRemoveAllTest : RemoveAllConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
