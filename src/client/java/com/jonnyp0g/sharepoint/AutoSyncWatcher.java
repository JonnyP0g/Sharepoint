package com.jonnyp0g.sharepoint;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

/**
 * Watches the player's local waypoints.json file for changes and, when
 * enabled, automatically re-exports it to a fixed "auto-sync" share so
 * other players (or a synced folder) always see the latest version
 * without a manual export step.
 */
class AutoSyncWatcher {
    private static final String AUTO_SYNC_SHARE_NAME = "auto-sync";
    private static final long DEBOUNCE_MILLIS = 750;

    private final WaypointShareService service;
    private final Consumer<String> statusCallback;
    private Thread watchThread;
    private volatile boolean running;

    AutoSyncWatcher(WaypointShareService service, Consumer<String> statusCallback) {
        this.service = service;
        this.statusCallback = statusCallback;
    }

    boolean isRunning() {
        return running;
    }

    synchronized void start() {
        if (running) {
            return;
        }

        Path watchTarget = service.getWaypointsPath();
        Path directory = watchTarget.getParent();
        if (directory == null) {
            statusCallback.accept("Auto-sync failed: could not resolve waypoints folder.");
            return;
        }

        try {
            java.nio.file.Files.createDirectories(directory);
        } catch (IOException e) {
            statusCallback.accept("Auto-sync failed: " + e.getMessage());
            return;
        }

        running = true;
        watchThread = new Thread(() -> runWatchLoop(directory, watchTarget.getFileName().toString()), "sharepoint-auto-sync");
        watchThread.setDaemon(true);
        watchThread.start();
        statusCallback.accept("Auto-sync enabled - watching " + watchTarget);
    }

    synchronized void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
    }

    private void runWatchLoop(Path directory, String fileName) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            directory.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE);

            long lastSync = 0;
            while (running) {
                WatchKey key;
                try {
                    key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (key == null) {
                    continue;
                }

                boolean relevant = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    Object context = event.context();
                    if (context != null && context.toString().equals(fileName)) {
                        relevant = true;
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }

                long now = System.currentTimeMillis();
                if (relevant && (now - lastSync) > DEBOUNCE_MILLIS) {
                    lastSync = now;
                    try {
                        service.exportWaypoints(AUTO_SYNC_SHARE_NAME);
                        statusCallback.accept("Auto-sync: waypoints updated and re-shared.");
                    } catch (IOException e) {
                        statusCallback.accept("Auto-sync export failed: " + e.getMessage());
                    }
                }
            }
        } catch (ClosedWatchServiceException ignored) {
            // Watch service was closed as part of shutdown; nothing to report.
        } catch (IOException e) {
            statusCallback.accept("Auto-sync stopped: " + e.getMessage());
        } finally {
            running = false;
        }
    }
}
