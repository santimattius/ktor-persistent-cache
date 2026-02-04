package io.github.santimattius.persistent.cache.doubles

import io.ktor.util.date.getTimeMillis
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source

/**
 * A fake in-memory FileSystem implementation for testing purposes.
 * This test double allows us to test file operations without touching the real filesystem.
 */
class FakeFileSystem : FileSystem() {

    private val files = mutableMapOf<Path, ByteArray>()
    private val directories = mutableSetOf<Path>()
    private val fileMetadata = mutableMapOf<Path, FakeMetadata>()

    data class FakeMetadata(
        val size: Long,
        val createdAtMillis: Long = getTimeMillis(),
        var lastModifiedAtMillis: Long = getTimeMillis()
    )

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        val existing = files[file] ?: byteArrayOf()
        return FakeSink { bytes ->
            files[file] = existing + bytes
            updateMetadata(file)
        }
    }

    override fun atomicMove(source: Path, target: Path) {
        val content = files.remove(source) ?: throw okio.IOException("Source not found: $source")
        val metadata = fileMetadata.remove(source)
        files[target] = content
        metadata?.let { fileMetadata[target] = it }
    }

    override fun canonicalize(path: Path): Path = path

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (mustCreate && directories.contains(dir)) {
            throw okio.IOException("Directory already exists: $dir")
        }
        directories.add(dir)
    }

    override fun createSymlink(source: Path, target: Path) {
        throw UnsupportedOperationException("Symlinks not supported in FakeFileSystem")
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val existed = files.remove(path) != null || directories.remove(path)
        fileMetadata.remove(path)
        if (mustExist && !existed) {
            throw okio.IOException("File not found: $path")
        }
    }

    override fun list(dir: Path): List<Path> {
        return files.keys.filter { it.parent == dir } +
               directories.filter { it.parent == dir && it != dir }
    }

    override fun listOrNull(dir: Path): List<Path>? {
        return if (directories.contains(dir) || dir.toString() == "/" || dir.toString().isEmpty()) {
            list(dir)
        } else {
            null
        }
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        return when {
            files.containsKey(path) -> {
                val meta = fileMetadata[path] ?: FakeMetadata(
                    size = files[path]?.size?.toLong() ?: 0L
                )
                FileMetadata(
                    isRegularFile = true,
                    isDirectory = false,
                    size = meta.size,
                    createdAtMillis = meta.createdAtMillis,
                    lastModifiedAtMillis = meta.lastModifiedAtMillis
                )
            }
            directories.contains(path) -> {
                FileMetadata(
                    isRegularFile = false,
                    isDirectory = true
                )
            }
            else -> null
        }
    }

    override fun openReadOnly(file: Path): FileHandle {
        throw UnsupportedOperationException("FileHandle not supported in FakeFileSystem")
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        throw UnsupportedOperationException("FileHandle not supported in FakeFileSystem")
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (mustCreate && files.containsKey(file)) {
            throw okio.IOException("File already exists: $file")
        }
        return FakeSink { bytes ->
            files[file] = bytes
            updateMetadata(file)
        }
    }

    override fun source(file: Path): Source {
        val content = files[file] ?: throw okio.IOException("File not found: $file")
        return FakeSource(content)
    }

    private fun updateMetadata(file: Path) {
        val size = files[file]?.size?.toLong() ?: 0L
        val existing = fileMetadata[file]
        if (existing != null) {
            existing.lastModifiedAtMillis = getTimeMillis()
            fileMetadata[file] = existing.copy(size = size)
        } else {
            fileMetadata[file] = FakeMetadata(size = size)
        }
    }

    // Test helper methods

    fun getFileContent(path: Path): ByteArray? = files[path]

    fun setFileContent(path: Path, content: ByteArray) {
        files[path] = content
        updateMetadata(path)
    }

    fun fileExists(path: Path): Boolean = files.containsKey(path)

    fun directoryExists(path: Path): Boolean = directories.contains(path)

    fun getAllFiles(): Set<Path> = files.keys.toSet()

    fun clear() {
        files.clear()
        directories.clear()
        fileMetadata.clear()
    }

    fun setLastModified(path: Path, timestamp: Long) {
        val meta = fileMetadata[path] ?: FakeMetadata(size = files[path]?.size?.toLong() ?: 0L)
        fileMetadata[path] = meta.copy(lastModifiedAtMillis = timestamp)
    }
}

/**
 * A simple Sink implementation that collects written bytes.
 */
private class FakeSink(private val onClose: (ByteArray) -> Unit) : Sink {
    private val buffer = mutableListOf<Byte>()

    override fun write(source: okio.Buffer, byteCount: Long) {
        repeat(byteCount.toInt()) {
            buffer.add(source.readByte())
        }
    }

    override fun flush() {}

    override fun timeout(): okio.Timeout = okio.Timeout.NONE

    override fun close() {
        onClose(buffer.toByteArray())
    }
}

/**
 * A simple Source implementation that reads from a byte array.
 */
private class FakeSource(private val content: ByteArray) : Source {
    private var position = 0

    override fun read(sink: okio.Buffer, byteCount: Long): Long {
        if (position >= content.size) return -1
        val toRead = minOf(byteCount.toInt(), content.size - position)
        sink.write(content, position, toRead)
        position += toRead
        return toRead.toLong()
    }

    override fun timeout(): okio.Timeout = okio.Timeout.NONE

    override fun close() {}
}
