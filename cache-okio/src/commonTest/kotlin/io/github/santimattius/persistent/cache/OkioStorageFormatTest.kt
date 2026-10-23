@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.StorageFormatConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [StorageFormatConformanceTest] against [OkioCacheFileSystem]. */
class OkioStorageFormatTest : StorageFormatConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
