package com.arcana.cloud.plugin.runtime.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;

/**
 * Shared storage for plugin binaries across cluster.
 *
 * <p>This store enables plugin JARs to be shared across multiple instances
 * in a Kubernetes cluster. It supports multiple backends:</p>
 * <ul>
 *   <li>Redis (for smaller plugins, default)</li>
 *   <li>Shared filesystem (e.g., NFS, PersistentVolume)</li>
 *   <li>S3-compatible object storage</li>
 * </ul>
 */
public class PluginBinaryStore {

    private static final Logger log = LoggerFactory.getLogger(PluginBinaryStore.class);

    private static final String PLUGIN_BINARY_PREFIX = "arcana:plugin:binary:";
    private static final Duration BINARY_TTL = Duration.ofDays(7);
    private static final long MAX_REDIS_SIZE = 10 * 1024 * 1024; // 10MB limit for Redis

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final Path sharedDirectory;
    private final Path localCacheDirectory;
    private final StorageMode storageMode;

    public enum StorageMode {
        REDIS,
        FILESYSTEM,
        HYBRID  // Use Redis for small plugins, filesystem for large ones
    }

    public PluginBinaryStore(
            RedisTemplate<String, byte[]> redisTemplate,
            Path sharedDirectory,
            Path localCacheDirectory,
            StorageMode storageMode) {
        this.redisTemplate = redisTemplate;
        this.sharedDirectory = sharedDirectory;
        this.localCacheDirectory = localCacheDirectory;
        this.storageMode = storageMode;

        ensureDirectoriesExist();
    }

    /**
     * Stores a plugin binary.
     *
     * @param pluginKey the plugin key
     * @param binaryPath path to the plugin JAR
     * @throws IOException if storage fails
     */
    public void storePlugin(String pluginKey, Path binaryPath) throws IOException {
        byte[] data = Files.readAllBytes(binaryPath);
        String fileName = binaryPath.getFileName().toString();

        log.info("Storing plugin {} binary ({} bytes)", pluginKey, data.length);

        switch (storageMode) {
            case REDIS -> storeInRedis(pluginKey, data, fileName);
            case FILESYSTEM -> storeInFilesystem(pluginKey, data, fileName);
            case HYBRID -> {
                if (data.length <= MAX_REDIS_SIZE) {
                    storeInRedis(pluginKey, data, fileName);
                } else {
                    storeInFilesystem(pluginKey, data, fileName);
                }
            }
        }

        // Also cache locally
        cacheLocally(pluginKey, data, fileName);
    }

    /**
     * Downloads a plugin binary.
     *
     * @param pluginKey the plugin key
     * @return path to the downloaded JAR, or null if not found
     */
    public Path downloadPlugin(String pluginKey) {
        // Check local cache first
        Path cached = getFromLocalCache(pluginKey);
        if (cached != null && Files.exists(cached)) {
            log.debug("Plugin {} found in local cache", pluginKey);
            return cached;
        }

        // Try to download from shared storage
        try {
            byte[] data = null;
            String fileName = null;

            switch (storageMode) {
                case REDIS -> {
                    data = getFromRedis(pluginKey);
                    fileName = getFileNameFromRedis(pluginKey);
                }
                case FILESYSTEM -> {
                    Path sharedPath = getFromFilesystem(pluginKey);
                    if (sharedPath != null) {
                        return sharedPath;
                    }
                }
                case HYBRID -> {
                    // Try Redis first, then filesystem
                    data = getFromRedis(pluginKey);
                    fileName = getFileNameFromRedis(pluginKey);
                    if (data == null) {
                        Path sharedPath = getFromFilesystem(pluginKey);
                        if (sharedPath != null) {
                            return sharedPath;
                        }
                    }
                }
            }

            if (data != null && fileName != null) {
                // Cache locally and return
                return cacheLocally(pluginKey, data, fileName);
            }

        } catch (Exception e) {
            log.error("Failed to download plugin: {}", pluginKey, e);
        }

        return null;
    }

    /**
     * Deletes a plugin binary from shared storage.
     *
     * @param pluginKey the plugin key
     */
    public void deletePlugin(String pluginKey) {
        log.info("Deleting plugin {} binary from shared storage", pluginKey);

        // Delete from Redis
        redisTemplate.delete(PLUGIN_BINARY_PREFIX + pluginKey);
        redisTemplate.delete(PLUGIN_BINARY_PREFIX + pluginKey + ":name");

        // Delete from filesystem
        try {
            Path sharedPath = sharedDirectory.resolve(pluginKey);
            if (Files.exists(sharedPath)) {
                Files.walk(sharedPath)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
            }
        } catch (IOException e) {
            log.warn("Failed to delete plugin directory: {}", pluginKey);
        }

        // Delete from local cache
        try {
            Path cachePath = localCacheDirectory.resolve(pluginKey);
            if (Files.exists(cachePath)) {
                Files.walk(cachePath)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete cached: {}", p);
                        }
                    });
            }
        } catch (IOException e) {
            log.warn("Failed to delete cached plugin: {}", pluginKey);
        }
    }

    /**
     * Checks if a plugin binary exists in shared storage.
     *
     * @param pluginKey the plugin key
     * @return true if exists
     */
    public boolean pluginExists(String pluginKey) {
        // Check Redis
        Boolean exists = redisTemplate.hasKey(PLUGIN_BINARY_PREFIX + pluginKey);
        if (Boolean.TRUE.equals(exists)) {
            return true;
        }

        // Check filesystem
        Path sharedPath = sharedDirectory.resolve(pluginKey);
        return Files.exists(sharedPath);
    }

    /**
     * Returns all stored plugin keys.
     *
     * @return set of plugin keys
     */
    public Set<String> listPlugins() {
        Set<String> keys = redisTemplate.keys(PLUGIN_BINARY_PREFIX + "*");
        if (keys != null) {
            return keys.stream()
                .filter(k -> !k.endsWith(":name"))
                .map(k -> k.substring(PLUGIN_BINARY_PREFIX.length()))
                .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private void storeInRedis(String pluginKey, byte[] data, String fileName) {
        String key = PLUGIN_BINARY_PREFIX + pluginKey;
        redisTemplate.opsForValue().set(key, data, BINARY_TTL);
        redisTemplate.opsForValue().set(key + ":name", fileName.getBytes(), BINARY_TTL);
        log.debug("Stored plugin {} in Redis ({} bytes)", pluginKey, data.length);
    }

    private void storeInFilesystem(String pluginKey, byte[] data, String fileName) throws IOException {
        Path pluginDir = sharedDirectory.resolve(pluginKey);
        Files.createDirectories(pluginDir);

        Path targetPath = pluginDir.resolve(fileName);
        Files.write(targetPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Stored plugin {} in filesystem: {}", pluginKey, targetPath);
    }

    private byte[] getFromRedis(String pluginKey) {
        String key = PLUGIN_BINARY_PREFIX + pluginKey;
        return redisTemplate.opsForValue().get(key);
    }

    private String getFileNameFromRedis(String pluginKey) {
        String key = PLUGIN_BINARY_PREFIX + pluginKey + ":name";
        byte[] nameBytes = redisTemplate.opsForValue().get(key);
        return nameBytes != null ? new String(nameBytes) : pluginKey + ".jar";
    }

    private Path getFromFilesystem(String pluginKey) {
        Path pluginDir = sharedDirectory.resolve(pluginKey);
        if (!Files.exists(pluginDir)) {
            return null;
        }

        try {
            return Files.list(pluginDir)
                .filter(p -> p.toString().endsWith(".jar"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            log.error("Failed to list plugin directory: {}", pluginKey, e);
            return null;
        }
    }

    private Path getFromLocalCache(String pluginKey) {
        Path pluginDir = localCacheDirectory.resolve(pluginKey);
        if (!Files.exists(pluginDir)) {
            return null;
        }

        try {
            return Files.list(pluginDir)
                .filter(p -> p.toString().endsWith(".jar"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Path cacheLocally(String pluginKey, byte[] data, String fileName) throws IOException {
        Path pluginDir = localCacheDirectory.resolve(pluginKey);
        Files.createDirectories(pluginDir);

        Path targetPath = pluginDir.resolve(fileName);
        Files.write(targetPath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.debug("Cached plugin {} locally: {}", pluginKey, targetPath);
        return targetPath;
    }

    private void ensureDirectoriesExist() {
        try {
            if (sharedDirectory != null) {
                Files.createDirectories(sharedDirectory);
            }
            if (localCacheDirectory != null) {
                Files.createDirectories(localCacheDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create directories", e);
        }
    }
}
