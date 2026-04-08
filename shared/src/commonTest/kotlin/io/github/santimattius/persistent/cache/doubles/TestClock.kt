package io.github.santimattius.persistent.cache.doubles

/**
 * A controllable clock for deterministic testing of TTL-based logic.
 * Replaces real-time delays with manual time advancement.
 */
class TestClock(initialTime: Long = 0L) {
    private var currentTime = initialTime

    fun now(): Long = currentTime

    fun advance(milliseconds: Long) {
        currentTime += milliseconds
    }
}
