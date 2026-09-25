package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

class PackDetailsScreen extends Screen {
    private final Screen parent;
    private final String name;
    PackDetailsScreen(Screen parent, String name) { super(Component.literal("Saved pack")); this.parent = parent; this.name = name; }
    @Override protected void init() {
        int w = Math.min(320, width - 24), left = (width - w) / 2;
        add("Preview & import", left, 56, w, () -> minecraft.gui.setScreen(new SharePreviewScreen(this, name)));
        add("Share with a code", left, 82, w, () -> minecraft.gui.setScreen(new TransferScreen(this, true, name)));
        add("Rename", left, 108, w / 2 - 3, () -> minecraft.gui.setScreen(new RenameShareScreen(parent, name)));
        add("Delete", left + w / 2 + 3, 108, w / 2 - 3, () -> minecraft.gui.setScreen(new ConfirmDeleteScreen(parent, name)));
        add("Back", left, height - 30, w, this::onClose);
    }
    private void add(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        String text = font.plainSubstrByWidth(name, width - 24);
        g.text(font, text, (width - font.width(text)) / 2, 24, 0xFFFFFFFF, true);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
