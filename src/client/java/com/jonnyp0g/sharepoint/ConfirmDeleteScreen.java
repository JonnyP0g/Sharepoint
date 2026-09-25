package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

class ConfirmDeleteScreen extends Screen {
    private final Screen parent;
    private final String shareName;

    ConfirmDeleteScreen(Screen parent, String shareName) {
        super(Component.literal("Delete Share"));
        this.parent = parent;
        this.shareName = shareName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> confirm())
            .bounds(centerX - 105, y, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
            .bounds(centerX + 5, y, 100, 20)
            .build());
    }

    private void confirm() {
        try {
            SharepointClient.SERVICE.deleteShare(shareName);
            SharepointClient.log("Deleted share '" + shareName + "'.");
        } catch (java.io.IOException e) {
            SharepointClient.log("Delete failed: " + e.getMessage());
        }
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        graphics.text(this.font, "Delete share '" + shareName + "'?", centerX - this.font.width("Delete share '" + shareName + "'?") / 2,
            this.height / 2 - 30, 0xFFFFFFFF, true);
        String warning = "This cannot be undone.";
        graphics.text(this.font, warning, centerX - this.font.width(warning) / 2, this.height / 2 - 16, 0xFFAAAAAA, false);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
