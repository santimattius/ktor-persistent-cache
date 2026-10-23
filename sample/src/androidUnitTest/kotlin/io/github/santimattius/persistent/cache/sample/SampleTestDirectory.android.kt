package io.github.santimattius.persistent.cache.sample

import java.io.File
import kotlin.random.Random

internal actual fun createSampleTestDirectory(): String {
    val directory = File(System.getProperty("java.io.tmpdir"), "ktor-persistent-cache-sample-${Random.nextLong()}")
    check(directory.mkdirs()) { "Could not create sample test directory: ${directory.absolutePath}" }
    return directory.absolutePath
}

internal actual fun deleteSampleTestDirectory(rootDirectory: String) {
    File(rootDirectory).deleteRecursively()
}
