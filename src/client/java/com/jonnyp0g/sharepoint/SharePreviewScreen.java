package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

class SharePreviewScreen extends Screen {
    private final Screen parent;
    private final String shareName;
    private String summary = "Loading...";
    private boolean valid;

    SharePreviewScreen(Screen parent, String shareName) {
        super(Component.literal("Preview Share"));
        this.parent = parent;
        this.shareName = shareName;
    }

    @Override
    protected void init() {
        try {
            byte[] data = SharepointClient.SERVICE.readShareBytes(shareName);
            WaypointShareService.validatePack(data);
            summary = WaypointPreview.summarize(data);
            valid = true;
        } catch (IOException e) {
            summary = "Could not read share: " + e.getMessage();
            valid = false;
        }

        int centerX = this.width / 2;
        int y = this.height - 32;

        this.addRenderableWidget(Button.builder(Component.literal("Replace & back up"), btn -> importAndClose())
            .bounds(centerX - 150, y, 190, 20)
            .build()).active = valid;
        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> this.onClose())
            .bounds(centerX + 50, y, 100, 20)
            .build());
    }

    private void importAndClose() {
        try {
            SharepointClient.SERVICE.importWaypoints(shareName);
            SharepointClient.log("Imported pack. Previous file backed up in sharepoint-backups.");
        } catch (IOException e) {
            summary = "Import failed: " + e.getMessage();
            return;
        }
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int w = Math.min(360, width - 24), left = (width - w) / 2;
        String title = font.plainSubstrByWidth("Preview: " + shareName, w);
        graphics.text(font, title, left, 22, 0xFFFFFFFF, true);
        int y = 46;
        for (var line : font.split(Component.literal(summary), w)) {
            graphics.text(font, line, left, y, 0xFFAAAAAA, false);
            y += 11;
        }
        for (var line : font.split(Component.literal("Import replaces your current waypoint file. A backup is saved first. Packs are not merged."), w)) {
            graphics.text(font, line, left, y + 12, 0xFFFFCC88, false);
            y += 11;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
