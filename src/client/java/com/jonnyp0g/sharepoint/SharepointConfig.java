package com.jonnyp0g.sharepoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small persisted settings file for Sharepoint, stored under the
 * Fabric config directory as {@code sharepoint.properties}.
 */
class SharepointConfig {
    private static final String AUTO_SYNC_KEY = "autoSyncEnabled";
    private static final String PEER_PORT_KEY = "peerPort";
    private static final String RELAY_HOST_KEY = "relayHost";
    private static final String RELAY_PORT_KEY = "relayPort";
    private static final int DEFAULT_PEER_PORT = 34521;
    private static final int DEFAULT_RELAY_PORT = 34600;

    private final Path configFile;
    private String relayUrl = bundledRelayUrl();
    private boolean autoSyncEnabled;
    private int peerPort = DEFAULT_PEER_PORT;
    private String relayHost = "";
    private int relayPort = DEFAULT_RELAY_PORT;

    SharepointConfig(Path configDir) {
        this.configFile = configDir.resolve("sharepoint.properties");
        load();
    }

    boolean isAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    String getRelayUrl() { return relayUrl; }

    void setRelayUrl(String url) {
        relayUrl = HostedRelayClient.validateUrl(url);
        save();
    }

    private static String bundledRelayUrl() {
        Properties defaults = new Properties();
        try (InputStream in = SharepointConfig.class.getResourceAsStream("/sharepoint-defaults.properties")) {
            if (in != null) defaults.load(in);
        } catch (IOException ignored) { }
        return defaults.getProperty("relayUrl", "");
    }

    void setAutoSyncEnabled(boolean enabled) {
        this.autoSyncEnabled = enabled;
        save();
    }

    int getPeerPort() {
        return peerPort;
    }

    void setPeerPort(int port) {
        this.peerPort = port;
        save();
    }

    /** Address of your relay server (e.g. your Minecraft server's IP), for cross-network sharing. Empty if not configured yet. */
    String getRelayHost() {
        return relayHost;
    }

    int getRelayPort() {
        return relayPort;
    }

    void setRelay(String host, int port) {
        this.relayHost = host;
        this.relayPort = port;
        save();
    }

    boolean isRelayConfigured() {
        return relayHost != null && !relayHost.isBlank();
    }

    private void load() {
        if (!Files.exists(configFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
            String savedRelayUrl = properties.getProperty("relayUrl", "").trim();
            if (!savedRelayUrl.isBlank()) relayUrl = savedRelayUrl;
            autoSyncEnabled = Boolean.parseBoolean(properties.getProperty(AUTO_SYNC_KEY, "false"));
            peerPort = Integer.parseInt(properties.getProperty(PEER_PORT_KEY, String.valueOf(DEFAULT_PEER_PORT)));
            relayHost = properties.getProperty(RELAY_HOST_KEY, "");
            relayPort = Integer.parseInt(properties.getProperty(RELAY_PORT_KEY, String.valueOf(DEFAULT_RELAY_PORT)));
        } catch (IOException | NumberFormatException ignored) {
            // Fall back to defaults if the config file is missing or corrupt.
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(AUTO_SYNC_KEY, String.valueOf(autoSyncEnabled));
        properties.setProperty("relayUrl", relayUrl);
        properties.setProperty(PEER_PORT_KEY, String.valueOf(peerPort));
        properties.setProperty(RELAY_HOST_KEY, relayHost == null ? "" : relayHost);
        properties.setProperty(RELAY_PORT_KEY, String.valueOf(relayPort));

        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            try (OutputStream out = Files.newOutputStream(configFile)) {
                properties.store(out, "Sharepoint mod settings");
            }
        } catch (IOException ignored) {
            // Non-fatal: settings just won't persist across restarts this time.
        }
    }
}
