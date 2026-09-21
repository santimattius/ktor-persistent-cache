package io.github.santimattius.persistent.cache

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct unit tests for [KotlinxIoCacheFileSystem]: the [CacheFileSystem]<[kotlinx.io.files.Path]>
 * adapter that lets `cache-core`'s [FileCacheStorage] run on top of kotlinx-io's real
 * [SystemFileSystem].
 *
 * Unlike `cache-okio`'s equivalent test (backed by the official `okio-fakefilesystem`),
 * kotlinx-io ships no in-memory fake filesystem in 0.9.1, so this test runs against a real,
 * unique temp directory per test instance instead (mirroring what `cache-okio`'s cold-start test
 * does deliberately, but here it is the only option).
 */
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
class KotlinxIoCacheFileSystemTest {

    private val subject = KotlinxIoCacheFileSystem()
    private lateinit var testRoot: Path

    @BeforeTest
    fun setUp() {
        testRoot = Path(SystemTemporaryDirectory, "ktor-cache-kotlinxio-unit-${Random.nextLong()}")
        SystemFileSystem.createDirectories(testRoot)
    }

    @AfterTest
    fun tearDown() {
        deleteRecursively(testRoot)
    }

    @Test
    fun `given base and segments when resolve then joins them into a single kotlinx io path`() {
        val resolved = subject.resolve(testRoot.toString(), "http_cache", "abc123_0.cache")

        assertEquals(Path(testRoot, "http_cache", "abc123_0.cache").toString(), resolved.toString())
    }

    @Test
    fun `given a resolved path when name is requested then returns the last segment`() {
        val resolved = subject.resolve(testRoot.toString(), "http_cache", "abc123_0.cache")

        assertEquals("abc123_0.cache", subject.name(resolved))
    }

    @Test
    fun `given a missing directory when createDirectories then it and its parents are created`() {
        val dir = subject.resolve(testRoot.toString(), "http_cache")

        assertFalse(subject.exists(dir))
        subject.createDirectories(dir)
        assertTrue(subject.exists(dir))
    }

    @Test
    fun `given bytes written to a path when read then the same bytes are returned`() {
        val file = subject.resolve(testRoot.toString(), "entry.cache")

        subject.write(file, byteArrayOf(1, 2, 3, 4))

        assertEquals(listOf<Byte>(1, 2, 3, 4), subject.read(file).toList())
    }

    @Test
    fun `given files under a directory when list then all direct children are returned`() {
        val dir = subject.resolve(testRoot.toString(), "http_cache")
        subject.createDirectories(dir)
        val fileA = subject.resolve(testRoot.toString(), "http_cache", "a.cache")
        val fileB = subject.resolve(testRoot.toString(), "http_cache", "b.cache")
        subject.write(fileA, byteArrayOf(1))
        subject.write(fileB, byteArrayOf(2))

        val listed = subject.list(dir).map { subject.name(it) }.toSet()

        assertEquals(setOf("a.cache", "b.cache"), listed)
    }

    @Test
    fun `given a missing directory when list then an empty list is returned`() {
        val missingDir = subject.resolve(testRoot.toString(), "does-not-exist")

        assertEquals(emptyList(), subject.list(missingDir))
    }

    @Test
    fun `given an existing file when delete then it no longer exists`() {
        val file = subject.resolve(testRoot.toString(), "entry.cache")
        subject.write(file, byteArrayOf(9))

        subject.delete(file)

        assertFalse(subject.exists(file))
    }

    @Test
    fun `given a missing file when delete then no exception is thrown`() {
        val file = subject.resolve(testRoot.toString(), "missing.cache")

        subject.delete(file)
    }

    @Test
    fun `given a written file when metadata is requested then size is reported`() {
        val file = subject.resolve(testRoot.toString(), "entry.cache")
        subject.write(file, byteArrayOf(1, 2, 3, 4, 5))

        val metadata = subject.metadata(file)

        assertNotNull(metadata)
        assertEquals(5L, metadata.size)
    }

    @Test
    fun `given a missing file when metadata is requested then null is returned`() {
        val file = subject.resolve(testRoot.toString(), "missing.cache")

        assertNull(subject.metadata(file))
    }
}

/** Recursively deletes [dir] (used only for test cleanup — not part of the production adapter). */
private fun deleteRecursively(dir: Path) {
    if (!SystemFileSystem.exists(dir)) return
    val metadata = SystemFileSystem.metadataOrNull(dir)
    if (metadata?.isDirectory == true) {
        SystemFileSystem.list(dir).forEach { deleteRecursively(it) }
    }
    SystemFileSystem.delete(dir, mustExist = false)
}
