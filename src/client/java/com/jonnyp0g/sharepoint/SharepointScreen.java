package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Small landing page; each task has its own focused screen. */
class SharepointScreen extends Screen {
    private final Screen parent;
    private boolean library;
    private String query = "";
    private int page;
    private int pages = 1;
    private String error = "";

    SharepointScreen(Screen parent) { super(Component.literal("Sharepoint")); this.parent = parent; }

    @Override protected void init() {
        int w = Math.min(360, width - 24);
        int left = (width - w) / 2;
        if (!library) {
            int y = Math.max(62, height / 2 - 42);
            button("Share waypoints", left, y, w, () -> minecraft.gui.setScreen(new TransferScreen(this, true, null)));
            button("Receive with a code", left, y + 28, w, () -> minecraft.gui.setScreen(new TransferScreen(this, false, null)));
            button("Saved packs", left, y + 56, w, () -> { library = true; rebuildWidgets(); });
        } else {
            EditBox search = new EditBox(font, left, 48, w, 20, Component.literal("Search saved packs"));
            search.setMaxLength(64);
            search.setValue(query);
            addRenderableWidget(search);
            // Apply explicitly to keep typing/focus stable while the list changes.
            button("Search", left, 74, w / 2 - 3, () -> { query = search.getValue(); page = 0; rebuildWidgets(); });
            button("Save current pack", left + w / 2 + 3, 74, w / 2 - 3, () -> {
                try {
                    SharepointClient.SERVICE.saveReceived(SharepointClient.SERVICE.readWaypointsBytes());
                    rebuildWidgets();
                } catch (IOException e) { error = e.getMessage(); }
            });
            try {
                List<String> packs = SharepointClient.SERVICE.listShares().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
                int count = Math.max(1, (height - 170) / 26);
                pages = Math.max(1, (packs.size() + count - 1) / count);
                page = Math.min(page, pages - 1);
                for (int i = page * count; i < Math.min(packs.size(), (page + 1) * count); i++) {
                    String name = packs.get(i);
                    button(font.plainSubstrByWidth(name, w - 18), left, 102 + (i - page * count) * 26, w,
                        () -> minecraft.gui.setScreen(new PackDetailsScreen(this, name)));
                }
                error = packs.isEmpty() ? "No saved packs match your search." : "";
                button("<", left, height - 62, 42, () -> { if (page > 0) { page--; rebuildWidgets(); } }).active = page > 0;
                button(">", left + w - 42, height - 62, 42, () -> { if (page + 1 < pages) { page++; rebuildWidgets(); } }).active = page + 1 < pages;
            } catch (IOException e) { error = "Could not load saved packs."; }
        }
        button("Settings", left, height - 30, w / 2 - 3, () -> minecraft.gui.setScreen(new RelaySettingsScreen(this)));
        button(library ? "Back" : "Close", left + w / 2 + 3, height - 30, w / 2 - 3,
            () -> { if (library) { library = false; rebuildWidgets(); } else onClose(); });
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        center(g, library ? "Saved packs" : "Sharepoint", 18, 0xFFFFFFFF);
        if (!library) {
            center(g, "Your waypoints. One code. Shared.", 36, 0xFFAAAAAA);
            center(g, SharepointClient.CONFIG.getRelayUrl().isBlank() ? "Set your relay URL in Settings to get started." : "Ready to share using your configured relay.", height - 54, 0xFFAAAAAA);
        } else {
            center(g, error.isBlank() ? "Page " + (page + 1) + " / " + pages : error, height - 56, 0xFFAAAAAA);
        }
    }

    private void center(GuiGraphicsExtractor g, String value, int y, int color) {
        String text = font.plainSubstrByWidth(value, width - 24);
        g.text(font, text, (width - font.width(text)) / 2, y, color, false);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
