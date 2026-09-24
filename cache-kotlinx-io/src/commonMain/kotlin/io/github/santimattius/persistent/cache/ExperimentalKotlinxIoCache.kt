package io.github.santimattius.persistent.cache

/**
 * Marks the kotlinx-io-backed [CacheFileSystem] ([KotlinxIoCacheFileSystem]) as experimental.
 *
 * kotlinx-io's own `kotlinx.io.files` package is Alpha-stability upstream (kotlinx-io 0.9.1), and
 * this backend is deliberately kept on the SAME version train as every other module in this
 * library rather than a separate versioning scheme (design decision #3, Engram #1505). Okio
 * remains the facade default.
 *
 * This is a `WARNING`-level opt-in — deliberately weaker than [InternalPersistentCacheApi]'s
 * `ERROR` level: that annotation gates an internal, unsupported backend SPI, while this one gates
 * a real, documented *feature* consumers deliberately choose to adopt. Opting in informs
 * consumers this backend may change shape as kotlinx-io's own `kotlinx.io.files` API matures,
 * without hard-blocking adoption the way an `ERROR`-level opt-in would.
 */
@RequiresOptIn(
    message = "KotlinxIoCacheFileSystem is experimental: it tracks kotlinx-io's own Alpha-stability " +
        "kotlinx.io.files API and may change shape between minor versions of this library. Okio " +
        "remains the recommended default backend.",
    level = RequiresOptIn.Level.WARNING
)
public annotation class ExperimentalKotlinxIoCache
