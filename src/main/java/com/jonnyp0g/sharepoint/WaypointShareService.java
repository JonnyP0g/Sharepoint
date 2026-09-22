package com.jonnyp0g.sharepoint;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class WaypointShareService {
    private static final String WAYPOINT_FILE_NAME = "waypoints.json";
    private final Path gameDir;
    private final Path sharedDir;

    WaypointShareService(Path gameDir) {
        this.gameDir = gameDir;
        this.sharedDir = gameDir.resolve("sharepoint-shared");
    }

    Path exportWaypoints(String shareName) throws IOException {
        Path source = resolveWaypointFile();
        if (!Files.exists(source)) {
            throw new IOException("Could not find " + WAYPOINT_FILE_NAME + ". Looked at: " + source);
        }

        Files.createDirectories(sharedDir);
        Path target = sharePath(shareName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    Path importWaypoints(String shareName) throws IOException {
        Path source = sharePath(shareName);
        if (!Files.exists(source)) {
            throw new IOException("Share does not exist: " + source);
        }

        Path target = resolveWaypointFile();
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    List<String> listShares() throws IOException {
        if (!Files.isDirectory(sharedDir)) {
            return List.of();
        }

        List<String> shares = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sharedDir, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                shares.add(fileName.substring(0, fileName.length() - 5));
            }
        }

        shares.sort(String::compareToIgnoreCase);
        return shares;
    }

    private Path sharePath(String shareName) {
        String normalized = shareName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (normalized.isBlank()) {
            normalized = "shared";
        }

        return sharedDir.resolve(normalized + ".json");
    }

    private Path resolveWaypointFile() {
        Path gameWaypoint = gameDir.resolve(WAYPOINT_FILE_NAME);
        if (Files.exists(gameWaypoint)) {
            return gameWaypoint;
        }

        return Path.of(System.getProperty("user.home"), ".lunarclient", "settings", "game", WAYPOINT_FILE_NAME);
    }
}
