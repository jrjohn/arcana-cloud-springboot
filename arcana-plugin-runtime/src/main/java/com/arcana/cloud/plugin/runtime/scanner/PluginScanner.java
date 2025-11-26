package com.arcana.cloud.plugin.runtime.scanner;

import com.arcana.cloud.plugin.runtime.osgi.OSGiPluginManager;
import org.osgi.framework.BundleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scans the plugins directory for plugin bundles and installs them.
 *
 * <p>Supports both initial scanning on startup and continuous monitoring
 * for hot deployment of new plugins.</p>
 */
public class PluginScanner {

    private static final Logger log = LoggerFactory.getLogger(PluginScanner.class);

    private final OSGiPluginManager pluginManager;
    private final Path pluginsDirectory;
    private ScheduledExecutorService watchScheduler;
    private WatchService watchService;
    private volatile boolean watching = false;

    /**
     * Creates a new plugin scanner.
     *
     * @param pluginManager the OSGi plugin manager
     * @param pluginsDirectory the plugins directory
     */
    public PluginScanner(OSGiPluginManager pluginManager, Path pluginsDirectory) {
        this.pluginManager = pluginManager;
        this.pluginsDirectory = pluginsDirectory;
    }

    /**
     * Scans and installs all plugin bundles in the plugins directory.
     *
     * @return list of installed plugin keys
     */
    public List<String> scanAndInstall() {
        List<String> installedPlugins = new ArrayList<>();

        try {
            if (!Files.exists(pluginsDirectory)) {
                log.info("Plugins directory does not exist: {}", pluginsDirectory);
                return installedPlugins;
            }

            log.info("Scanning for plugins in: {}", pluginsDirectory);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDirectory, "*.jar")) {
                for (Path jarPath : stream) {
                    try {
                        log.debug("Found plugin JAR: {}", jarPath.getFileName());
                        pluginManager.installPlugin(jarPath);
                        // The plugin key will be extracted from the bundle manifest
                        installedPlugins.add(jarPath.getFileName().toString());
                    } catch (BundleException e) {
                        log.error("Failed to install plugin: {}", jarPath.getFileName(), e);
                    }
                }
            }

            // Also check subdirectories for exploded bundles
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDirectory, Files::isDirectory)) {
                for (Path dir : stream) {
                    Path manifest = dir.resolve("META-INF/MANIFEST.MF");
                    if (Files.exists(manifest)) {
                        try {
                            log.debug("Found exploded plugin: {}", dir.getFileName());
                            pluginManager.installPlugin(dir);
                            installedPlugins.add(dir.getFileName().toString());
                        } catch (BundleException e) {
                            log.error("Failed to install exploded plugin: {}", dir.getFileName(), e);
                        }
                    }
                }
            }

            log.info("Installed {} plugins", installedPlugins.size());

        } catch (IOException e) {
            log.error("Error scanning plugins directory", e);
        }

        return installedPlugins;
    }

    /**
     * Starts watching for new plugins (hot deployment).
     *
     * @param pollInterval poll interval in milliseconds
     */
    public void startWatching(long pollInterval) {
        if (watching) {
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            pluginsDirectory.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            watchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "plugin-watcher");
                t.setDaemon(true);
                return t;
            });

            watchScheduler.scheduleWithFixedDelay(this::pollForChanges, pollInterval, pollInterval,
                TimeUnit.MILLISECONDS);

            watching = true;
            log.info("Started watching plugins directory for changes");

        } catch (IOException e) {
            log.error("Failed to start plugin directory watcher", e);
        }
    }

    /**
     * Stops watching for plugins.
     */
    public void stopWatching() {
        if (!watching) {
            return;
        }

        watching = false;

        if (watchScheduler != null) {
            watchScheduler.shutdown();
            try {
                watchScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }

        log.info("Stopped watching plugins directory");
    }

    private void pollForChanges() {
        try {
            WatchKey key = watchService.poll();
            if (key == null) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path fileName = pathEvent.context();
                Path fullPath = pluginsDirectory.resolve(fileName);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    handleNewPlugin(fullPath);
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    handleDeletedPlugin(fullPath);
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    handleModifiedPlugin(fullPath);
                }
            }

            key.reset();

        } catch (Exception e) {
            log.error("Error polling for plugin changes", e);
        }
    }

    private void handleNewPlugin(Path path) {
        if (!isPluginFile(path)) {
            return;
        }

        log.info("New plugin detected: {}", path.getFileName());
        try {
            // Wait a bit for file to be fully written
            Thread.sleep(1000);
            pluginManager.installPlugin(path);
        } catch (BundleException | InterruptedException e) {
            log.error("Failed to install new plugin: {}", path.getFileName(), e);
        }
    }

    private void handleDeletedPlugin(Path path) {
        if (!isPluginFile(path)) {
            return;
        }

        log.info("Plugin file deleted: {}", path.getFileName());
        // Note: The actual uninstall should be handled by tracking which bundle
        // was installed from which file. For now, we just log.
    }

    private void handleModifiedPlugin(Path path) {
        if (!isPluginFile(path)) {
            return;
        }

        log.info("Plugin file modified: {}", path.getFileName());
        // Note: For updates, we'd need to track the bundle and call update()
    }

    private boolean isPluginFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".jar") ||
               (Files.isDirectory(path) &&
                Files.exists(path.resolve("META-INF/MANIFEST.MF")));
    }
}
