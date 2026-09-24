package io.github.santimattius.persistent.cache

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

/**
 * Shared real-temp-directory setup for `cache-kotlinx-io`'s conformance-suite subclasses.
 *
 * Unlike `cache-okio` (backed by the official `okio-fakefilesystem` for its conformance suites,
 * design decision #4, Engram #1505), kotlinx-io ships no in-memory fake filesystem in 0.9.1, so
 * every conformance suite for this backend runs against a real, unique-per-instance temp
 * directory instead. [CacheConformanceSupport.root][io.github.santimattius.persistent.cache.conformance.support.CacheConformanceSupport]
 * is `protected open`, so each subclass overrides it with the result of [freshTempDirectory].
 */
internal object KotlinxIoConformanceTestSupport {

    /** Creates a fresh, real, writable temp directory and returns its path as a [String]. */
    fun freshTempDirectory(prefix: String): String {
        val dir = Path(SystemTemporaryDirectory, "$prefix-${Random.nextLong()}")
        SystemFileSystem.createDirectories(dir)
        return dir.toString()
    }

    /** Recursively deletes the directory at [root]. Best-effort: never throws. */
    fun deleteRecursively(root: String) {
        try {
            deleteRecursively(Path(root))
        } catch (_: Exception) {
            // Best-effort test cleanup only.
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        val metadata = SystemFileSystem.metadataOrNull(path)
        if (metadata?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { deleteRecursively(it) }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }
}
