package io.github.santimattius.persistent.cache.sample

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
internal actual fun createSampleTestDirectory(): String {
    val directory = "${NSTemporaryDirectory()}ktor-persistent-cache-sample-${Random.nextLong()}"
    check(
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        ),
    ) { "Could not create sample test directory: $directory" }
    return directory
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteSampleTestDirectory(rootDirectory: String) {
    NSFileManager.defaultManager.removeItemAtPath(rootDirectory, error = null)
}
