package io.github.santimattius.persistent.cache

import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct unit tests for [OkioCacheFileSystem]: the [CacheFileSystem]<[okio.Path]> adapter that
 * lets `cache-core`'s [FileCacheStorage] run on top of a real Okio [okio.FileSystem].
 *
 * Uses the official `com.squareup.okio:okio-fakefilesystem` double (design decision #4,
 * Engram #1505) instead of the hand-rolled `shared/commonTest/doubles/FakeFileSystem.kt`, which
 * is deleted in this PR (task 2.8).
 */
@OptIn(InternalPersistentCacheApi::class)
class OkioCacheFileSystemTest {

    private val delegate = FakeFileSystem()
    private val subject = OkioCacheFileSystem(delegate)

    @Test
    fun `given base and segments when resolve then joins them into a single okio path`() {
        val resolved = subject.resolve("/root", "http_cache", "abc123_0.cache")

        assertEquals("/root/http_cache/abc123_0.cache", resolved.toString())
    }

    @Test
    fun `given a resolved path when name is requested then returns the last segment`() {
        val resolved = subject.resolve("/root", "http_cache", "abc123_0.cache")

        assertEquals("abc123_0.cache", subject.name(resolved))
    }

    @Test
    fun `given a missing directory when createDirectories then it and its parents are created`() {
        val dir = subject.resolve("/root", "http_cache")

        assertFalse(subject.exists(dir))
        subject.createDirectories(dir)
        assertTrue(subject.exists(dir))
    }

    @Test
    fun `given bytes written to a path when read then the same bytes are returned`() {
        val dir = subject.resolve("/root")
        subject.createDirectories(dir)
        val file = subject.resolve("/root", "entry.cache")

        subject.write(file, byteArrayOf(1, 2, 3, 4))

        assertEquals(listOf<Byte>(1, 2, 3, 4), subject.read(file).toList())
    }

    @Test
    fun `given files under a directory when list then all direct children are returned`() {
        val dir = subject.resolve("/root", "http_cache")
        subject.createDirectories(dir)
        val fileA = subject.resolve("/root", "http_cache", "a.cache")
        val fileB = subject.resolve("/root", "http_cache", "b.cache")
        subject.write(fileA, byteArrayOf(1))
        subject.write(fileB, byteArrayOf(2))

        val listed = subject.list(dir).map { subject.name(it) }.toSet()

        assertEquals(setOf("a.cache", "b.cache"), listed)
    }

    @Test
    fun `given an existing file when delete then it no longer exists`() {
        val dir = subject.resolve("/root")
        subject.createDirectories(dir)
        val file = subject.resolve("/root", "entry.cache")
        subject.write(file, byteArrayOf(9))

        subject.delete(file)

        assertFalse(subject.exists(file))
    }

    @Test
    fun `given a missing file when delete then no exception is thrown`() {
        val file = subject.resolve("/root", "missing.cache")

        subject.delete(file)
    }

    @Test
    fun `given a written file when metadata is requested then size is reported`() {
        val dir = subject.resolve("/root")
        subject.createDirectories(dir)
        val file = subject.resolve("/root", "entry.cache")
        subject.write(file, byteArrayOf(1, 2, 3, 4, 5))

        val metadata = subject.metadata(file)

        assertNotNull(metadata)
        assertEquals(5L, metadata.size)
    }

    @Test
    fun `given a missing file when metadata is requested then null is returned`() {
        val file = subject.resolve("/root", "missing.cache")

        assertNull(subject.metadata(file))
    }
}
