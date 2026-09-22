package com.jonnyp0g.sharepoint;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SharepointClient implements ClientModInitializer {
    private static final WaypointShareService SERVICE = new WaypointShareService(FabricLoader.getInstance().getGameDir());

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(buildCommand()));
    }

    private LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> buildCommand() {
        return ClientCommandManager.literal("sharepoint")
            .then(ClientCommandManager.literal("export")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        try {
                            Path exported = SERVICE.exportWaypoints(name);
                            sendMessage("Exported waypoints to " + exported);
                            return 1;
                        } catch (IOException exception) {
                            sendMessage("Export failed: " + exception.getMessage());
                            return 0;
                        }
                    })))
            .then(ClientCommandManager.literal("import")
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        try {
                            Path imported = SERVICE.importWaypoints(name);
                            sendMessage("Imported waypoints from share '" + name + "' into " + imported);
                            return 1;
                        } catch (IOException exception) {
                            sendMessage("Import failed: " + exception.getMessage());
                            return 0;
                        }
                    })))
            .then(ClientCommandManager.literal("list")
                .executes(context -> {
                    try {
                        List<String> shares = SERVICE.listShares();
                        if (shares.isEmpty()) {
                            sendMessage("No shared waypoint files found.");
                        } else {
                            sendMessage("Available shares: " + String.join(", ", shares));
                        }
                        return 1;
                    } catch (IOException exception) {
                        sendMessage("List failed: " + exception.getMessage());
                        return 0;
                    }
                }));
    }

    private static void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[Sharepoint] " + message), false);
        }
    }
}
