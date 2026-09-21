@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.VaryKeysConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [VaryKeysConformanceTest] against [OkioCacheFileSystem]. */
class OkioVaryKeysTest : VaryKeysConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
