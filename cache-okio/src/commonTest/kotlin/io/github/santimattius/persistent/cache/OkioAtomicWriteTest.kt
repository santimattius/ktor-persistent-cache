@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.AtomicWriteConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [AtomicWriteConformanceTest] against [OkioCacheFileSystem]. */
class OkioAtomicWriteTest : AtomicWriteConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
