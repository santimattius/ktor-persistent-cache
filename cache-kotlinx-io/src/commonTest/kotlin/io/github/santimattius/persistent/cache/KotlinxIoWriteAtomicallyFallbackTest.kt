package io.github.santimattius.persistent.cache

import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * kotlinx-io's `FileSystem` is a sealed interface, so a wrapper cannot be injected. These tests
 * drive the internal `writeAtomically(path, bytes, move)` overload with a `move` that mimics
 * platforms where `atomicMove` refuses to replace an existing destination or is unsupported.
 */
@OptIn(InternalPersistentCacheApi::class, ExperimentalKotlinxIoCache::class)
class KotlinxIoWriteAtomicallyFallbackTest {

    private val subject = KotlinxIoCacheFileSystem()
    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = Path(SystemTemporaryDirectory, "ktor-cache-kio-fallback-${Random.nextLong()}")
        SystemFileSystem.createDirectories(root)
    }

    @AfterTest
    fun tearDown() = KotlinxIoConformanceTestSupport.deleteRecursively(root.toString())

    private fun names(): List<String> = SystemFileSystem.list(root).map { it.name }.sorted()

    private val rejectOverExisting: (Path, Path) -> Unit = { source, destination ->
        if (SystemFileSystem.exists(destination)) throw IOException("destination exists")
        SystemFileSystem.atomicMove(source, destination)
    }

    @Test
    fun `given a move that rejects an existing destination when writing over an entry then the new bytes replace it and no tmp remains`() {
        val target = Path(root, "entry.cache")
        subject.write(target, byteArrayOf(1))

        subject.writeAtomically(target, byteArrayOf(2, 3), rejectOverExisting)

        assertContentEquals(byteArrayOf(2, 3), subject.read(target))
        assertEquals(listOf("entry.cache"), names())
    }

    @Test
    fun `given an unsupported atomic move when writing then the bytes are still written and no tmp remains`() {
        val target = Path(root, "entry.cache")
        subject.write(target, byteArrayOf(1))

        subject.writeAtomically(target, byteArrayOf(4, 5)) { _, _ ->
            throw UnsupportedOperationException("no atomic move")
        }

        assertContentEquals(byteArrayOf(4, 5), subject.read(target))
        assertEquals(listOf("entry.cache"), names())
    }

    @Test
    fun `given a move that always fails when writing then the failure propagates and no tmp remains`() {
        val target = Path(root, "entry.cache")

        assertFailsWith<IOException> {
            subject.writeAtomically(target, byteArrayOf(9)) { _, _ -> throw IOException("disk full") }
        }

        assertFalse(subject.exists(target))
        assertTrue(names().isEmpty())
    }
}
