package com.jonnyp0g.sharepoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

class RenameShareScreen extends Screen {
    private final Screen parent;
    private final String currentName;
    private EditBox nameBox;

    RenameShareScreen(Screen parent, String currentName) {
        super(Component.literal("Rename Share"));
        this.parent = parent;
        this.currentName = currentName;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 10;

        nameBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("New name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(currentName);
        this.addRenderableWidget(nameBox);

        this.addRenderableWidget(Button.builder(Component.literal("Rename"), btn -> confirm())
            .bounds(centerX - 105, y + 26, 100, 20)
            .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> this.onClose())
            .bounds(centerX + 5, y + 26, 100, 20)
            .build());
    }

    private void confirm() {
        String newName = nameBox.getValue().trim();
        if (newName.isBlank()) {
            SharepointClient.log("Enter a name first.");
            return;
        }

        try {
            SharepointClient.SERVICE.renameShare(currentName, newName);
            SharepointClient.log("Renamed '" + currentName + "' to '" + newName + "'.");
        } catch (java.io.IOException e) {
            SharepointClient.log("Rename failed: " + e.getMessage());
            return;
        }

        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String title = "Rename '" + currentName + "'";
        graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 30, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
