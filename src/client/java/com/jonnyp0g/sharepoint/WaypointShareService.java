package com.jonnyp0g.sharepoint;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import com.google.gson.JsonParser;

/**
 * Handles reading, writing, exporting and importing the Lunar Client
 * {@code waypoints.json} file, plus the local "shared" folder that acts as
 * the staging area for both manual sharing and auto-sync.
 */
class WaypointShareService {
    private static final String WAYPOINT_FILE_NAME = "waypoints.json";
    private final Path gameDir;
    private final Path sharedDir;

    WaypointShareService(Path gameDir) {
        this.gameDir = gameDir;
        this.sharedDir = gameDir.resolve("sharepoint-shared");
    }

    Path exportWaypoints(String shareName) throws IOException {
        byte[] data = readWaypointsBytes();
        return saveShare(shareName, data);
    }

    Path importWaypoints(String shareName) throws IOException {
        Path source = sharePath(shareName);
        if (!Files.exists(source)) {
            throw new IOException("Share does not exist: " + source);
        }

        return writeWaypointsBytes(readPackBytes(source));
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

    /** Reads the raw bytes of the player's current local waypoints file. */
    byte[] readWaypointsBytes() throws IOException {
        Path source = resolveWaypointFile();
        if (!Files.exists(source)) {
            throw new IOException("Could not find " + WAYPOINT_FILE_NAME + ". Looked at: " + source);
        }

        return readPackBytes(source);
    }

    /** Overwrites the player's local waypoints file with the given bytes. */
    Path writeWaypointsBytes(byte[] data) throws IOException {
        validatePack(data);
        Path target = resolveWaypointFile();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        if (Files.exists(target)) {
            Path backups = gameDir.resolve("sharepoint-backups");
            Files.createDirectories(backups);
            Files.copy(target, backups.resolve("waypoints-" + java.util.UUID.randomUUID() + ".json"));
        }
        Path temporary = Files.createTempFile(target.getParent(), "sharepoint-", ".tmp");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    static void validatePack(byte[] data) throws IOException {
        if (data.length == 0 || data.length > HostedRelayClient.MAX_BYTES)
            throw new IOException("Packs must be between 1 byte and 5 MiB.");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
            var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
            reader.setStrictness(com.google.gson.Strictness.STRICT);
            var root = JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Trailing data.");
            if (!root.isJsonArray() && !root.isJsonObject())
                throw new IllegalArgumentException("Expected a JSON object or array.");
            // Limit nesting before previewing untrusted documents.
            checkDepth(root, 0);
        } catch (RuntimeException | StackOverflowError e) {
            throw new IOException("This file is not a supported JSON waypoint pack.", e);
        }
    }

    private static void checkDepth(com.google.gson.JsonElement value, int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON is too deeply nested.");
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) checkDepth(entry.getValue(), depth + 1);
        if (value.isJsonArray()) for (var entry : value.getAsJsonArray()) checkDepth(entry, depth + 1);
    }

    String saveReceived(byte[] data) throws IOException {
        validatePack(data);
        Files.createDirectories(sharedDir);
        String name = "received-" + java.util.UUID.randomUUID().toString().substring(0, 12);
        Files.write(sharePath(name), data, StandardOpenOption.CREATE_NEW);
        return name;
    }

    /** Saves the given bytes into the shared folder under a sanitized name. */
    Path saveShare(String shareName, byte[] data) throws IOException {
        Files.createDirectories(sharedDir);
        Path target = sharePath(shareName);
        Files.write(target, data);
        return target;
    }

    /** Reads the raw bytes of a share, for previewing before import. */
    byte[] readShareBytes(String shareName) throws IOException {
        Path source = sharePath(shareName);
        if (!Files.exists(source)) {
            throw new IOException("Share does not exist: " + source);
        }

        return readPackBytes(source);
    }

    private static byte[] readPackBytes(Path source) throws IOException {
        try (var input = Files.newInputStream(source)) {
            byte[] data = input.readNBytes(HostedRelayClient.MAX_BYTES + 1);
            if (data.length > HostedRelayClient.MAX_BYTES) throw new IOException("Pack is larger than 5 MiB.");
            return data;
        }
    }

    void deleteShare(String shareName) throws IOException {
        Path target = sharePath(shareName);
        if (!Files.exists(target)) {
            throw new IOException("Share does not exist: " + target);
        }

        Files.delete(target);
    }

    Path renameShare(String fromName, String toName) throws IOException {
        Path source = sharePath(fromName);
        if (!Files.exists(source)) {
            throw new IOException("Share does not exist: " + source);
        }

        Path target = sharePath(toName);
        if (Files.exists(target)) {
            throw new IOException("A share named '" + toName + "' already exists.");
        }

        return Files.move(source, target);
    }

    Path getWaypointsPath() {
        return resolveWaypointFile();
    }

    Path getSharedDir() {
        return sharedDir;
    }

    boolean shareExists(String shareName) {
        return Files.exists(sharePath(shareName));
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
