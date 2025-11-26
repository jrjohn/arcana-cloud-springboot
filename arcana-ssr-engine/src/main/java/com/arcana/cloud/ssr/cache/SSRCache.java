package com.arcana.cloud.ssr.cache;

import java.util.Optional;

/**
 * Cache interface for SSR rendered content.
 */
public interface SSRCache {

    /**
     * Gets a cached value.
     *
     * @param key the cache key
     * @return the cached value or empty if not present
     */
    Optional<String> get(String key);

    /**
     * Puts a value in the cache.
     *
     * @param key the cache key
     * @param value the value to cache
     * @param ttlSeconds time-to-live in seconds
     */
    void put(String key, String value, int ttlSeconds);

    /**
     * Removes a cached value.
     *
     * @param key the cache key
     */
    void remove(String key);

    /**
     * Clears all cached values.
     */
    void clear();

    /**
     * Clears cached values matching a pattern.
     *
     * @param pattern the pattern to match (supports * wildcards)
     */
    void clearByPattern(String pattern);

    /**
     * Gets cache statistics.
     *
     * @return the cache statistics
     */
    CacheStatistics getStatistics();

    /**
     * Cache statistics.
     */
    record CacheStatistics(
        long hits,
        long misses,
        long size,
        long evictions
    ) {
        public double hitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
