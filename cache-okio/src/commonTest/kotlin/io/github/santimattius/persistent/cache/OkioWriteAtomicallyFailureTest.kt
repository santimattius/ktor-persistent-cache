@file:OptIn(InternalPersistentCacheApi::class)

package io.github.santimattius.persistent.cache

import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkioWriteAtomicallyFailureTest {

    @Test
    fun `given the move fails when writeAtomically is called then the target is untouched and the tmp file is removed`() {
        // Given
        val fake = FakeFileSystem()
        val failing = object : ForwardingFileSystem(fake) {
            override fun atomicMove(source: Path, target: Path) = throw IOException("move failed")
        }
        val target = "/cache/entry.cache".toPath()
        fake.createDirectories(target.parent!!)
        fake.write(target) { writeUtf8("old") }

        // When
        assertFailsWith<IOException> {
            OkioCacheFileSystem(failing).writeAtomically(target, "new".encodeToByteArray())
        }

        // Then
        assertEquals("old", fake.read(target) { readUtf8() })
        assertEquals(listOf(target), fake.list(target.parent!!))
    }
}
