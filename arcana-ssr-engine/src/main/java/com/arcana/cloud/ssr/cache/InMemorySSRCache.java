package com.arcana.cloud.ssr.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * In-memory implementation of SSRCache.
 *
 * <p>Uses a ConcurrentHashMap with TTL-based expiration.
 * Suitable for single-node deployments or development.</p>
 */
@Component
@ConditionalOnMissingBean(name = "redisSSRCache")
public class InMemorySSRCache implements SSRCache {

    private static final Logger log = LoggerFactory.getLogger(InMemorySSRCache.class);

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final ScheduledExecutorService cleanupExecutor;

    public InMemorySSRCache() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ssr-cache-cleanup");
            t.setDaemon(true);
            return t;
        });

        // Run cleanup every minute
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
        log.info("In-memory SSR cache initialized");
    }

    @Override
    public Optional<String> get(String key) {
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }

        if (entry.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            evictions.incrementAndGet();
            return Optional.empty();
        }

        hits.incrementAndGet();
        return Optional.of(entry.value());
    }

    @Override
    public void put(String key, String value, int ttlSeconds) {
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        cache.put(key, new CacheEntry(value, expiresAt));
        log.debug("Cached SSR content: {} (TTL: {}s)", key, ttlSeconds);
    }

    @Override
    public void remove(String key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("Cleared {} entries from SSR cache", size);
    }

    @Override
    public void clearByPattern(String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*");
        Pattern compiled = Pattern.compile(regex);

        int removed = 0;
        var iterator = cache.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (compiled.matcher(key).matches()) {
                iterator.remove();
                removed++;
            }
        }

        log.info("Cleared {} entries matching pattern: {}", removed, pattern);
    }

    @Override
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            hits.get(),
            misses.get(),
            cache.size(),
            evictions.get()
        );
    }

    private void cleanup() {
        int removed = 0;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired()) {
                iterator.remove();
                removed++;
                evictions.incrementAndGet();
            }
        }

        if (removed > 0) {
            log.debug("Cleaned up {} expired SSR cache entries", removed);
        }
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record CacheEntry(String value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
