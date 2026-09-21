package io.github.santimattius.persistent.cache

/**
 * Provides access to the platform-specific cache directory.
 *
 * This interface is used to abstract the retrieval of the cache directory path,
 * allowing for different implementations on various platforms (e.g., Android, iOS, JVM).
 *
 * BREAKING CHANGE from the pre-split `:shared` API: [cacheDirectory] returns [String], not
 * `okio.Path`, so `cache-core` has zero Okio/kotlinx-io dependencies. There is no deprecated
 * `okio.Path`-returning overload — see `docs/MIGRATION.md` (accepted hard break, Engram #1506).
 */
public interface CacheDirectoryProvider {

    /**
     * Provides the cache directory path for the current platform.
     */
    public val cacheDirectory: String
}

/**
 * Returns the platform-specific [CacheDirectoryProvider] used to resolve the cache directory path.
 *
 * On Android, uses the application cache directory.
 * On iOS, uses the app's caches directory in the sandbox.
 * On JVM, uses a directory under the system temp directory.
 *
 * @return The [CacheDirectoryProvider] for the current platform.
 */
public expect fun getCacheDirectoryProvider(): CacheDirectoryProvider
