@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import io.github.santimattius.persistent.cache.conformance.CacheExpirationConformanceTest
import okio.fakefilesystem.FakeFileSystem

/** Verifies [CacheExpirationConformanceTest] against [OkioCacheFileSystem]. */
class OkioCacheExpirationTest : CacheExpirationConformanceTest<okio.Path>() {
    override fun createFileSystem(): CacheFileSystem<okio.Path> = OkioCacheFileSystem(FakeFileSystem())
}
