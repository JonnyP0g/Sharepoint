package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

class RelaySettingsScreen extends Screen {
    private final Screen parent;
    private EditBox urlBox;
    private String draft;
    private String error = "";

    RelaySettingsScreen(Screen parent) {
        super(Component.literal("Relay settings"));
        this.parent = parent;
        draft = SharepointClient.CONFIG.getRelayUrl();
    }

    @Override protected void init() {
        int w = Math.min(360, width - 24), left = (width - w) / 2;
        urlBox = new EditBox(font, left, 72, w, 20, Component.literal("HTTPS relay URL"));
        urlBox.setMaxLength(256);
        urlBox.setValue(draft);
        urlBox.setResponder(value -> draft = value);
        addRenderableWidget(urlBox);
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try { SharepointClient.CONFIG.setRelayUrl(urlBox.getValue()); onClose(); }
            catch (IllegalArgumentException e) { error = e.getMessage(); }
        }).bounds(left, 104, w / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(left + w / 2 + 3, 104, w / 2 - 3, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int left = (width - Math.min(360, width - 24)) / 2;
        g.text(font, "Relay settings", left, 20, 0xFFFFFFFF, true);
        g.text(font, "Use the same HTTPS URL as your friends.", left, 42, 0xFFAAAAAA, false);
        g.text(font, "Relay URL", left, 58, 0xFFFFFFFF, false);
        g.text(font, font.plainSubstrByWidth(error, width - 24), left, 138, 0xFFFF8888, false);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
