package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.polaris_vehicle.content.menu.FuelExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class FuelExtractorScreen extends AbstractContainerScreen<FuelExtractorMenu> {
    public FuelExtractorScreen(
            FuelExtractorMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = FuelExtractorMenu.IMAGE_WIDTH;
        imageHeight = FuelExtractorMenu.IMAGE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                FuelExtractorUiSpec.BACKGROUND,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                imageWidth,
                imageHeight,
                imageWidth,
                imageHeight
        );

        int horizontalWidth = FuelExtractorUiSpec.horizontalProgressWidth(
                menu.progress(),
                menu.totalProgress()
        );
        if (horizontalWidth > 0) {
            graphics.blit(
                    FuelExtractorUiSpec.HORIZONTAL_PROGRESS,
                    leftPos + FuelExtractorUiSpec.HORIZONTAL_PROGRESS_X,
                    topPos + FuelExtractorUiSpec.HORIZONTAL_PROGRESS_Y,
                    0.0F,
                    0.0F,
                    horizontalWidth,
                    FuelExtractorUiSpec.HORIZONTAL_PROGRESS_HEIGHT,
                    FuelExtractorUiSpec.HORIZONTAL_PROGRESS_WIDTH,
                    FuelExtractorUiSpec.HORIZONTAL_PROGRESS_HEIGHT
            );
        }

        for (int segment = 0; segment < FuelExtractorUiSpec.VERTICAL_PROGRESS_COUNT; segment++) {
            int height = FuelExtractorUiSpec.verticalFuelSegmentHeight(
                    menu.storedFuelMl(),
                    menu.fuelCapacityMl(),
                    segment
            );
            if (height <= 0) {
                continue;
            }
            int sourceY = FuelExtractorUiSpec.VERTICAL_PROGRESS_HEIGHT - height;
            graphics.blit(
                    FuelExtractorUiSpec.VERTICAL_PROGRESS,
                    leftPos + FuelExtractorUiSpec.verticalProgressX(segment),
                    topPos + FuelExtractorUiSpec.VERTICAL_PROGRESS_Y + sourceY,
                    0.0F,
                    sourceY,
                    FuelExtractorUiSpec.VERTICAL_PROGRESS_WIDTH,
                    height,
                    FuelExtractorUiSpec.VERTICAL_PROGRESS_WIDTH,
                    FuelExtractorUiSpec.VERTICAL_PROGRESS_HEIGHT
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderSlotHighlight(
            GuiGraphics graphics,
            Slot slot,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (slot.index >= FuelExtractorMenu.MACHINE_SLOT_COUNT) {
            super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
            return;
        }
        int color = getSlotColor(slot.index);
        graphics.fill(slot.x - 1, slot.y, slot.x + 17, slot.y + 16, color);
        graphics.fill(slot.x, slot.y - 1, slot.x + 16, slot.y, color);
        graphics.fill(slot.x, slot.y + 16, slot.x + 16, slot.y + 17, color);
    }

}
