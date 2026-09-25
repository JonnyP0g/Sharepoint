package com.jonnyp0g.sharepoint;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SharepointClient implements ClientModInitializer {
    static final WaypointShareService SERVICE = new WaypointShareService(FabricLoader.getInstance().getGameDir());
    static final SharepointConfig CONFIG = new SharepointConfig(FabricLoader.getInstance().getConfigDir());
    static final PeerShareService PEER_SERVICE = new PeerShareService(SERVICE);
    static final AutoSyncWatcher AUTO_SYNC = new AutoSyncWatcher(SERVICE, SharepointClient::log);

    private static volatile String lastStatus = "";

    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sharepoint", "general"));

    private static final KeyMapping OPEN_SCREEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.sharepoint.open_screen",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_P,
        CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(buildCommand()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SCREEN_KEY.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new SharepointScreen(null));
                }
            }
        });

        if (CONFIG.isAutoSyncEnabled()) {
            AUTO_SYNC.start();
        }
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> buildCommand() {
        return ClientCommands.literal("sharepoint")
            .executes(context -> {
                Minecraft.getInstance().gui.setScreen(new SharepointScreen(Minecraft.getInstance().gui.screen()));
                return 1;
            })
            .then(ClientCommands.literal("open")
                .executes(context -> {
                    Minecraft.getInstance().gui.setScreen(new SharepointScreen(Minecraft.getInstance().gui.screen()));
                    return 1;
                }))
            .then(ClientCommands.literal("export")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        try {
                            Path exported = SERVICE.exportWaypoints(name);
                            log("Exported waypoints to " + exported);
                            return 1;
                        } catch (IOException exception) {
                            log("Export failed: " + exception.getMessage());
                            return 0;
                        }
                    })))
            .then(ClientCommands.literal("import")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        try {
                            Path imported = SERVICE.importWaypoints(name);
                            log("Imported waypoints from share '" + name + "' into " + imported);
                            return 1;
                        } catch (IOException exception) {
                            log("Import failed: " + exception.getMessage());
                            return 0;
                        }
                    })))
            .then(ClientCommands.literal("list")
                .executes(context -> {
                    try {
                        List<String> shares = SERVICE.listShares();
                        if (shares.isEmpty()) {
                            log("No shared waypoint files found.");
                        } else {
                            log("Available shares: " + String.join(", ", shares));
                        }
                        return 1;
                    } catch (IOException exception) {
                        log("List failed: " + exception.getMessage());
                        return 0;
                    }
                }))
            .then(ClientCommands.literal("delete")
                .then(ClientCommands.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        try {
                            SERVICE.deleteShare(name);
                            log("Deleted share '" + name + "'.");
                            return 1;
                        } catch (IOException exception) {
                            log("Delete failed: " + exception.getMessage());
                            return 0;
                        }
                    })))
            .then(ClientCommands.literal("rename")
                .then(ClientCommands.argument("from", StringArgumentType.word())
                    .then(ClientCommands.argument("to", StringArgumentType.word())
                        .executes(context -> {
                            String from = StringArgumentType.getString(context, "from");
                            String to = StringArgumentType.getString(context, "to");
                            try {
                                SERVICE.renameShare(from, to);
                                log("Renamed '" + from + "' to '" + to + "'.");
                                return 1;
                            } catch (IOException exception) {
                                log("Rename failed: " + exception.getMessage());
                                return 0;
                            }
                        }))))
            .then(ClientCommands.literal("autosync")
                .then(ClientCommands.literal("on")
                    .executes(context -> {
                        AUTO_SYNC.start();
                        CONFIG.setAutoSyncEnabled(true);
                        return 1;
                    }))
                .then(ClientCommands.literal("off")
                    .executes(context -> {
                        AUTO_SYNC.stop();
                        CONFIG.setAutoSyncEnabled(false);
                        log("Auto-sync disabled.");
                        return 1;
                    })))
            .then(ClientCommands.literal("host")
                .executes(context -> {
                    PEER_SERVICE.host(CONFIG.getPeerPort(), SharepointClient::log);
                    return 1;
                })
                .then(ClientCommands.argument("port", IntegerArgumentType.integer(1024, 65535))
                    .executes(context -> {
                        int port = IntegerArgumentType.getInteger(context, "port");
                        CONFIG.setPeerPort(port);
                        PEER_SERVICE.host(port, SharepointClient::log);
                        return 1;
                    })))
            .then(ClientCommands.literal("connect")
                .then(ClientCommands.argument("host", StringArgumentType.word())
                    .then(ClientCommands.argument("port", IntegerArgumentType.integer(1, 65535))
                        .executes(context -> {
                            String host = StringArgumentType.getString(context, "host");
                            int port = IntegerArgumentType.getInteger(context, "port");
                            log("Connecting to " + host + ":" + port + "...");
                            PEER_SERVICE.receive(host, port, "received", SharepointClient::log);
                            return 1;
                        }))))
            .then(ClientCommands.literal("relay")
                .then(ClientCommands.literal("set")
                    .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                        .executes(context -> {
                            try {
                                CONFIG.setRelayUrl(StringArgumentType.getString(context, "url"));
                                log("HTTPS relay saved.");
                                return 1;
                            } catch (IllegalArgumentException e) { log(e.getMessage()); return 0; }
                        })))
                .then(ClientCommands.literal("share").executes(context -> {
                    Minecraft.getInstance().gui.setScreen(new TransferScreen(null, true, null));
                    return 1;
                }))
                .then(ClientCommands.literal("join").executes(context -> {
                    Minecraft.getInstance().gui.setScreen(new TransferScreen(null, false, null));
                    return 1;
                })))
            .then(ClientCommands.literal("legacy-relay")
                .then(ClientCommands.literal("set")
                    .then(ClientCommands.argument("host", StringArgumentType.word())
                        .then(ClientCommands.argument("port", IntegerArgumentType.integer(1, 65535))
                            .executes(context -> {
                                String host = StringArgumentType.getString(context, "host");
                                int port = IntegerArgumentType.getInteger(context, "port");
                                CONFIG.setRelay(host, port);
                                log("Relay set to " + host + ":" + port);
                                return 1;
                            }))))
                .then(ClientCommands.literal("share")
                    .executes(context -> {
                        if (!CONFIG.isRelayConfigured()) {
                            log("No legacy relay configured. Use /sharepoint legacy-relay set <host> <port> first.");
                            return 0;
                        }
                        String code = generateCode();
                        log("Your relay code: " + code + " - tell your friend to run /sharepoint legacy-relay join " + code);
                        PEER_SERVICE.hostViaRelay(CONFIG.getRelayHost(), CONFIG.getRelayPort(), code, SharepointClient::log);
                        return 1;
                    }))
                .then(ClientCommands.literal("join")
                    .then(ClientCommands.argument("code", StringArgumentType.word())
                        .executes(context -> {
                            if (!CONFIG.isRelayConfigured()) {
                                log("No legacy relay configured. Use /sharepoint legacy-relay set <host> <port> first.");
                                return 0;
                            }
                            String code = StringArgumentType.getString(context, "code");
                            log("Joining relay with code " + code + "...");
                            PEER_SERVICE.receiveViaRelay(CONFIG.getRelayHost(), CONFIG.getRelayPort(), code, "received", SharepointClient::log);
                            return 1;
                        }))));
    }

    static String generateCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I to avoid confusion when read aloud
        java.util.random.RandomGenerator random = java.util.random.RandomGenerator.getDefault();
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    static void log(String message) {
        lastStatus = message;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) client.player.sendSystemMessage(Component.literal("[Sharepoint] " + message));
        });
    }

    static String getLastStatus() {
        return lastStatus;
    }
}
