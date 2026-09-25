package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** All I/O is off the render thread. Completion only opens a preview if still here. */
class TransferScreen extends Screen {
    private final Screen parent;
    private final boolean sending;
    private final String packName;
    private String code = "";
    private String draft = "";
    private String status = "";
    private String receivedName;
    private long expiresAt;
    private boolean busy;
    private boolean error;
    private EditBox input;
    private Button primary;
    private Button copy;

    TransferScreen(Screen parent, boolean sending, String packName) {
        super(Component.literal(sending ? "Share waypoints" : "Receive waypoints"));
        this.parent = parent;
        this.sending = sending;
        this.packName = packName;
    }

    @Override protected void init() {
        int w = Math.min(360, width - 24), left = (width - w) / 2;
        input = new EditBox(font, left, 72, w, 20, Component.literal(sending ? "Your share code" : "Enter share code"));
        input.setMaxLength(32);
        input.setValue(sending ? displayCode() : draft);
        input.setEditable(!sending && !busy);
        if (!sending) input.setResponder(value -> {
            if (!draft.equals(value)) receivedName = null;
            draft = value;
            if (primary != null && receivedName == null) primary.setMessage(Component.literal("Download & preview"));
        });
        addRenderableWidget(input);
        primary = addRenderableWidget(Button.builder(Component.literal(sending ? "Create share code" : "Download & preview"), b -> transfer())
            .bounds(left, 102, sending ? w / 2 - 3 : w, 20).build());
        primary.active = !busy;
        if (sending) {
            copy = addRenderableWidget(Button.builder(Component.literal("Copy code"), b -> {
                minecraft.keyboardHandler.setClipboard(displayCode());
                status = "Code copied. Send it to your friend.";
            }).bounds(left + w / 2 + 3, 102, w / 2 - 3, 20).build());
            copy.active = !code.isEmpty() && System.currentTimeMillis() < expiresAt;
        }
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.gui.setScreen(new RelaySettingsScreen(this)))
            .bounds(left, height - 30, w / 2 - 3, 20).build()).active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(left + w / 2 + 3, height - 30, w / 2 - 3, 20).build());
    }

    private String displayCode() { return code.length() == 10 ? code.substring(0, 5) + "-" + code.substring(5) : code; }

    private void transfer() {
        if (busy) return;
        if (!sending && receivedName != null) {
            minecraft.gui.setScreen(new SharePreviewScreen(this, receivedName));
            return;
        }
        String relay = SharepointClient.CONFIG.getRelayUrl();
        if (relay.isBlank()) { status = "Add your relay URL in Settings first."; error = true; return; }
        String requestedCode = draft;
        busy = true;
        error = false;
        status = (sending ? "Uploading waypoint pack..." : "Downloading waypoint pack...")
            + " A sleeping relay may take up to two minutes to wake up.";
        rebuildWidgets();
        CompletableFuture.supplyAsync(() -> {
            try {
                HostedRelayClient client = new HostedRelayClient();
                if (sending) {
                    byte[] data = packName == null ? SharepointClient.SERVICE.readWaypointsBytes() : SharepointClient.SERVICE.readShareBytes(packName);
                    WaypointShareService.validatePack(data);
                    return (Object) client.upload(relay, data);
                }
                return (Object) SharepointClient.SERVICE.saveReceived(client.download(relay, requestedCode));
            } catch (Exception e) { throw new CompletionException(e); }
        }).whenComplete((result, failure) -> minecraft.execute(() -> {
            busy = false;
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                status = cause.getMessage() == null ? "Could not connect to the relay. Try again." : cause.getMessage();
                error = true;
            } else if (sending) {
                HostedRelayClient.Upload upload = (HostedRelayClient.Upload) result;
                code = upload.code();
                expiresAt = System.currentTimeMillis() + upload.ttlSeconds() * 1000L;
                status = "Ready. Copy this code to your friend.";
            } else {
                receivedName = (String) result;
                status = "Downloaded. Your existing waypoints are unchanged.";
                if (minecraft.gui.screen() == this) {
                    minecraft.gui.setScreen(new SharePreviewScreen(this, receivedName));
                    return;
                }
                SharepointClient.log("Waypoint pack received. Find it in Saved packs.");
            }
            if (minecraft.gui.screen() == this) rebuildWidgets();
        }));
    }

    @Override public void tick() {
        if (copy != null) copy.active = !code.isEmpty() && System.currentTimeMillis() < expiresAt;
        if (!sending && receivedName != null && primary != null) primary.setMessage(Component.literal("Open preview"));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int w = Math.min(360, width - 24), left = (width - w) / 2;
        g.text(font, sending ? "Share waypoints" : "Receive waypoints", left, 20, 0xFFFFFFFF, true);
        String subtitle = sending ? (packName == null ? "Share your current waypoint pack." : "Share saved pack: " + packName) : "Paste your friend's code below.";
        g.text(font, font.plainSubstrByWidth(subtitle, w), left, 42, 0xFFAAAAAA, false);
        int y = 134;
        for (var line : font.split(Component.literal(status), w)) {
            if (y > height - 68) break;
            g.text(font, line, left, y, error ? 0xFFFF8888 : 0xFF99DDBB, false);
            y += 11;
        }
        if (sending && !code.isEmpty()) {
            long seconds = Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
            g.text(font, seconds == 0 ? "Code expired. Create a new one." : "Expires in " + seconds / 60 + "m " + seconds % 60 + "s", left, height - 52, 0xFFAAAAAA, false);
        }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
