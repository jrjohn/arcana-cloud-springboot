package com.arcana.cloud.ssr.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-backed implementation of SSRCache.
 *
 * <p>Provides distributed caching for SSR content across multiple nodes.
 * Requires Redis connection and spring-data-redis.</p>
 */
@Component("redisSSRCache")
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(name = "arcana.ssr.cache.type", havingValue = "redis")
public class RedisSSRCache implements SSRCache {

    private static final Logger log = LoggerFactory.getLogger(RedisSSRCache.class);
    private static final String KEY_PREFIX = "arcana:ssr:";

    private final StringRedisTemplate redisTemplate;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public RedisSSRCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("Redis SSR cache initialized");
    }

    @Override
    public Optional<String> get(String key) {
        String redisKey = KEY_PREFIX + key;
        String value = redisTemplate.opsForValue().get(redisKey);

        if (value == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }

        hits.incrementAndGet();
        return Optional.of(value);
    }

    @Override
    public void put(String key, String value, int ttlSeconds) {
        String redisKey = KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, Duration.ofSeconds(ttlSeconds));
        log.debug("Cached SSR content in Redis: {} (TTL: {}s)", key, ttlSeconds);
    }

    @Override
    public void remove(String key) {
        String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
    }

    @Override
    public void clear() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared {} entries from Redis SSR cache", keys.size());
        }
    }

    @Override
    public void clearByPattern(String pattern) {
        String redisPattern = KEY_PREFIX + pattern.replace("*", "*");
        Set<String> keys = redisTemplate.keys(redisPattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            evictions.addAndGet(keys.size());
            log.info("Cleared {} entries matching pattern: {}", keys.size(), pattern);
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        // Get approximate size from Redis
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        long size = keys != null ? keys.size() : 0;

        return new CacheStatistics(
            hits.get(),
            misses.get(),
            size,
            evictions.get()
        );
    }
}
