@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.InitializationConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [InitializationConformanceTest] against [OkioCacheFileSystem]. */
class OkioInitializationTest : InitializationConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
