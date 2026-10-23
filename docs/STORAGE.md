# Storage behavior

How `FileCacheStorage` (the shared engine in `cache-core`, used by every backend) keeps entries
on disk: what it guarantees, and what it does not.

## On-disk layout

Each entry is one file under `<cache root>/<directory>/`:

```
<sha256 hex of the URL, 64 chars>_<vary hash>.cache
```

- `vary hash` is `0` when the response has no `Vary` keys, otherwise the first 16 hex chars of
  the SHA-256 of the sorted `key=value;key=value` string.
- The file starts with a 4-byte header (`K`, `P`, `C`, format version `1`) followed by the
  ProtoBuf payload. A file with a missing or unknown header is treated as corrupt and deleted.
- `find` also checks that the stored vary keys equal the requested ones, so two different vary
  sets can never be served from each other's entry.
- Writes go to `<name>.tmp` next to the target and are then atomically moved onto it. `*.tmp`
  files never end in `.cache`, so lookups ignore them; leftovers from a crash are deleted when a
  storage starts and by `clear()`.

On startup the storage also deletes `*.cache` files whose name does not match the layout above
(files written by versions that used a 32-bit vary suffix). Existing caches from earlier
versions are therefore discarded once; this costs cache misses, never errors.

## Eviction

When `maxSize > 0`, every `store` ends with a cleanup that removes expired entries and then
evicts the least recently used entries until the total size fits `maxSize`.

- The storage keeps an in-memory index (`file -> size, stored timestamp, last access`). It is
  built lazily by scanning the directory once, on the first cleanup, and then kept in sync by
  `store`, `find`, `findAll`, `remove`, `removeAll` and `clear`. After that, cleanup reads no
  files. Memory cost is metadata only.
- Recency is real LRU: `find` and `findAll` refresh an entry's last access in memory.
- After a restart the index is rebuilt from disk, and last access falls back to the stored
  timestamp, so eviction order is write order until entries are read again.
- With `maxSize <= 0` no index is built and nothing is evicted by size.

## Concurrency

- One `FileCacheStorage` instance per cache directory is the supported configuration. Calls on
  that instance are serialized by an internal mutex.
- Atomic writes mean a concurrent reader (for example a second instance reading the same
  directory) never observes a half-written entry.
- Two instances (or processes) that **write or evict** in the same directory are unsupported:
  each keeps its own index and may delete files the other still counts. The worst outcome is a
  cache miss, but there is no cross-instance or cross-process lock.

## Expiry: client TTL and HTTP `expires`

There are two independent clocks:

1. **Storage TTL** (`ttl`, this library). An entry older than `ttl` milliseconds, measured from
   when it was stored, is dropped by `find`, `findAll` and cleanup. `ttl <= 0` disables it.
2. **HTTP freshness** (Ktor `HttpCache`). `CachedResponseData.expires` is computed by Ktor from
   `Cache-Control: max-age`/`s-maxage`, or the `Expires` header, and stored with the entry.

Verified against the Ktor 3.6.0 sources (`HttpCache.kt`, `HttpCacheEntry.kt`): after
`storage.find(...)` returns an entry, `HttpCache` calls `shouldValidate(cache.expires, ...)`. A
response whose `expires` is in the future is served without a network call; a stale one is
revalidated (`If-None-Match` / `If-Modified-Since` from the cached headers), or refetched, and
`must-revalidate`, `no-cache` and `max-stale` are honoured. So **HTTP freshness is already
enforced by Ktor from `expires`**, and this library does not duplicate it.

`ttl` therefore acts as an upper bound on how long an entry is retained on disk, not as the
freshness rule. An entry can be HTTP-stale yet still stored (it stays useful for conditional
revalidation), and an HTTP-fresh entry is still removed once it outlives `ttl`.
