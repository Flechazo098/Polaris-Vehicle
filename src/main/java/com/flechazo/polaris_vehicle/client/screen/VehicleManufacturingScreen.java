package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.menu.VehicleManufacturingMenu;
import com.flechazo.polaris_vehicle.item.VehicleItem;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingCategory;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingMaterials;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

public final class VehicleManufacturingScreen extends AbstractContainerScreen<VehicleManufacturingMenu> {
    private static final String TEXTURE_ROOT =
            "textures/gui/container/vehicle_manufacturing_table/";
    private static final ResourceLocation BACKGROUND = texture("background.png");
    private static final ResourceLocation BUTTON_LEFT = texture("button_left.png");
    private static final ResourceLocation BUTTON_LEFT_PRESSED = texture("button_left_pressed.png");
    private static final ResourceLocation BUTTON_RIGHT = texture("button_right.png");
    private static final ResourceLocation BUTTON_RIGHT_PRESSED = texture("button_right_pressed.png");
    private static final ResourceLocation SCROLL_TRACK = texture("scroll_track.png");
    private static final ResourceLocation SCROLL_THUMB = texture("scroll_thumb.png");
    private static final ResourceLocation UNSELECTED = texture("unselected.png");
    private static final ResourceLocation SELECTED = texture("selected.png");
    private static final ResourceLocation SLOT_NORMAL = texture("slot_variant_b.png");
    private static final ResourceLocation SLOT_PRESSED = texture("slot_variant_a.png");
    private static final int MISSING_MATERIAL_OVERLAY = 0x66FF4040;
    private static final int PAGE_TEXT_COLOR = 0xFF303438;
    private static final int SCROLL_STEP = VehicleManufacturingUiSpec.LIST_ROW_STEP;

    private final VehicleGuiPreview vehiclePreview = VehicleGuiPreview.manufacturing();
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int pressedPageButton = -1;
    private VehicleManufacturingCategory lastCategory;

    public VehicleManufacturingScreen(
            VehicleManufacturingMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = VehicleManufacturingMenu.IMAGE_WIDTH;
        imageHeight = VehicleManufacturingMenu.IMAGE_HEIGHT;
        lastCategory = menu.category();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        VehicleManufacturingCategory category = menu.category();
        if (category != lastCategory) {
            lastCategory = category;
            scrollOffset = 0;
        }
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderCustomTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        blitExact(graphics, BACKGROUND, leftPos, topPos, imageWidth, imageHeight);
        renderPlayerInventoryHover(graphics, mouseX, mouseY);
        updateVehiclePreview();
        vehiclePreview.render(graphics, leftPos, topPos, partialTick);
        renderPageButtons(graphics);
        renderRecipeList(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderMaterials(graphics, mouseX, mouseY);
        renderResultFrame(graphics, mouseX, mouseY);
    }

    private void renderResultFrame(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = logicalLocalX(mouseX);
        double localY = logicalLocalY(mouseY);
        boolean hovered = inside(
                localX,
                localY,
                VehicleManufacturingMenu.RESULT_FRAME_X,
                VehicleManufacturingMenu.RESULT_FRAME_Y,
                VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                VehicleManufacturingUiSpec.LIST_SLOT_SIZE
        );
        blitExact(
                graphics,
                hovered ? SELECTED : UNSELECTED,
                leftPos + VehicleManufacturingMenu.RESULT_FRAME_X,
                topPos + VehicleManufacturingMenu.RESULT_FRAME_Y,
                VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                VehicleManufacturingUiSpec.LIST_SLOT_SIZE
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component page = Component.translatable(menu.category().translationKey());
        graphics.drawCenteredString(
                font,
                page,
                VehicleManufacturingUiSpec.PAGE_X + VehicleManufacturingUiSpec.PAGE_WIDTH / 2,
                VehicleManufacturingUiSpec.PAGE_Y + 2,
                PAGE_TEXT_COLOR
        );
    }

    @Override
    protected void renderSlotHighlight(
            GuiGraphics graphics,
            Slot slot,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        int menuIndex = menu.slots.indexOf(slot);
        if (menuIndex >= VehicleManufacturingMenu.PLAYER_HOTBAR_START) {
            renderHotbarHighlight(graphics, slot.x, slot.y, getSlotColor(menuIndex));
            return;
        }
        renderCompactHighlight(graphics, slot.x, slot.y, getSlotColor(menuIndex));
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            Slot slot,
            String countString
    ) {
        int menuIndex = menu.slots.indexOf(slot);
        boolean hotbar = menuIndex >= VehicleManufacturingMenu.PLAYER_HOTBAR_START;
        int size = hotbar
                ? VehicleManufacturingUiSpec.HOTBAR_ICON_SIZE
                : VehicleManufacturingUiSpec.COMPACT_ICON_SIZE;
        int offset = hotbar
                ? VehicleManufacturingUiSpec.HOTBAR_ICON_OFFSET
                : VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET;
        float scale = size / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(slot.x + offset, slot.y + offset, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int seed = slot.x + slot.y * imageWidth;
        if (slot.isFake()) {
            graphics.renderFakeItem(stack, 0, 0);
        } else {
            graphics.renderItem(stack, 0, 0, seed);
        }
        graphics.renderItemDecorations(font, stack, 0, 0, countString);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = logicalLocalX(mouseX);
        double localY = logicalLocalY(mouseY);
        if (vehiclePreview.beginDrag(localX, localY, button)) {
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inside(
                localX,
                localY,
                VehicleManufacturingUiSpec.LEFT_BUTTON_X,
                VehicleManufacturingUiSpec.PAGE_BUTTON_Y,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE
        )) {
            pressedPageButton = VehicleManufacturingMenu.PREVIOUS_PAGE_BUTTON;
            sendButton(pressedPageButton);
            return true;
        }
        if (inside(
                localX,
                localY,
                VehicleManufacturingUiSpec.RIGHT_BUTTON_X,
                VehicleManufacturingUiSpec.PAGE_BUTTON_Y,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE
        )) {
            pressedPageButton = VehicleManufacturingMenu.NEXT_PAGE_BUTTON;
            sendButton(pressedPageButton);
            return true;
        }
        int maxScroll = maxScroll();
        int thumbY = VehicleManufacturingUiSpec.thumbY(scrollOffset, maxScroll);
        if (maxScroll > 0 && inside(
                localX,
                localY,
                VehicleManufacturingUiSpec.SCROLL_THUMB_X,
                thumbY,
                VehicleManufacturingUiSpec.SCROLL_THUMB_WIDTH,
                VehicleManufacturingUiSpec.SCROLL_THUMB_HEIGHT
        )) {
            draggingScrollbar = true;
            scrollbarGrabOffset = localY - thumbY;
            return true;
        }
        if (maxScroll > 0 && inside(
                localX,
                localY,
                VehicleManufacturingUiSpec.SCROLL_THUMB_X,
                VehicleManufacturingUiSpec.SCROLL_TRACK_Y,
                VehicleManufacturingUiSpec.SCROLL_THUMB_WIDTH,
                VehicleManufacturingUiSpec.SCROLL_TRACK_HEIGHT
        )) {
            draggingScrollbar = true;
            scrollbarGrabOffset = VehicleManufacturingUiSpec.SCROLL_THUMB_HEIGHT / 2.0;
            updateScrollFromMouse(localY);
            return true;
        }
        int recipeIndex = VehicleManufacturingUiSpec.recipeIndexAt(
                localX,
                localY,
                scrollOffset,
                menu.visibleRecipes().size()
        );
        if (recipeIndex >= 0) {
            sendButton(VehicleManufacturingMenu.RECIPE_BUTTON_BASE + recipeIndex);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (button == 0 && draggingScrollbar) {
            updateScrollFromMouse(logicalLocalY(mouseY));
            return true;
        }
        if (vehiclePreview.drag(button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = draggingScrollbar
                || pressedPageButton >= 0
                || vehiclePreview.endDrag(button);
        draggingScrollbar = false;
        pressedPageButton = -1;
        return handled || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        double localX = logicalLocalX(mouseX);
        double localY = logicalLocalY(mouseY);
        if (vehiclePreview.scroll(localX, localY, verticalAmount)) {
            return true;
        }
        if (inside(
                localX,
                localY,
                VehicleManufacturingUiSpec.LIST_X,
                VehicleManufacturingUiSpec.LIST_Y,
                VehicleManufacturingUiSpec.LIST_WIDTH,
                VehicleManufacturingUiSpec.LIST_HEIGHT
        ) && maxScroll() > 0) {
            scrollOffset = Math.clamp(
                    scrollOffset - (long) (int) Math.signum(verticalAmount) * SCROLL_STEP,
                    0,
                    maxScroll()
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        vehiclePreview.clear();
        super.removed();
    }

    private void renderPageButtons(GuiGraphics graphics) {
        blitExact(
                graphics,
                pressedPageButton == VehicleManufacturingMenu.PREVIOUS_PAGE_BUTTON
                        ? BUTTON_LEFT_PRESSED
                        : BUTTON_LEFT,
                leftPos + VehicleManufacturingUiSpec.LEFT_BUTTON_X,
                topPos + VehicleManufacturingUiSpec.PAGE_BUTTON_Y,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE
        );
        blitExact(
                graphics,
                pressedPageButton == VehicleManufacturingMenu.NEXT_PAGE_BUTTON
                        ? BUTTON_RIGHT_PRESSED
                        : BUTTON_RIGHT,
                leftPos + VehicleManufacturingUiSpec.RIGHT_BUTTON_X,
                topPos + VehicleManufacturingUiSpec.PAGE_BUTTON_Y,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE,
                VehicleManufacturingUiSpec.PAGE_BUTTON_SIZE
        );
    }

    private void renderPlayerInventoryHover(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = logicalLocalX(mouseX);
        double localY = logicalLocalY(mouseY);
        for (int menuIndex = VehicleManufacturingMenu.PLAYER_MAIN_START;
             menuIndex < VehicleManufacturingMenu.PLAYER_HOTBAR_START;
             menuIndex++) {
            Slot slot = menu.slots.get(menuIndex);
            if (inside(
                    localX,
                    localY,
                    slot.x,
                    slot.y,
                    VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                    VehicleManufacturingUiSpec.LIST_SLOT_SIZE
            )) {
                blitExact(
                        graphics,
                        SELECTED,
                        leftPos + slot.x,
                        topPos + slot.y,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE
                );
                return;
            }
        }
    }

    private void renderRecipeList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RecipeHolder<VehicleManufacturingRecipe>> recipes = menu.visibleRecipes();
        int hoveredRecipe = VehicleManufacturingUiSpec.recipeIndexAt(
                logicalLocalX(mouseX),
                logicalLocalY(mouseY),
                scrollOffset,
                recipes.size()
        );
        graphics.enableScissor(
                leftPos + VehicleManufacturingUiSpec.LIST_X,
                topPos + VehicleManufacturingUiSpec.LIST_Y,
                leftPos + VehicleManufacturingUiSpec.LIST_X + VehicleManufacturingUiSpec.LIST_WIDTH,
                topPos + VehicleManufacturingUiSpec.LIST_Y + VehicleManufacturingUiSpec.LIST_HEIGHT
        );
        for (int index = 0; index < recipes.size(); index++) {
            int column = index % VehicleManufacturingUiSpec.LIST_COLUMNS;
            int row = index / VehicleManufacturingUiSpec.LIST_COLUMNS;
            int x = leftPos + VehicleManufacturingUiSpec.LIST_SLOT_X
                    + column * VehicleManufacturingUiSpec.LIST_COLUMN_STEP;
            int y = topPos + VehicleManufacturingUiSpec.LIST_SLOT_Y
                    + row * VehicleManufacturingUiSpec.LIST_ROW_STEP - scrollOffset;
            boolean active = index == menu.selectedRecipeIndex() || index == hoveredRecipe;
            blitExact(
                    graphics,
                    active ? SLOT_PRESSED : SLOT_NORMAL,
                    x,
                    y,
                    VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                    VehicleManufacturingUiSpec.LIST_SLOT_SIZE
            );
            ItemStack result = recipes.get(index).value().getResultItem(menuPlayer().registryAccess());
            renderCompactItem(graphics, result, x, y, null);
        }
        graphics.disableScissor();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        blitExact(
                graphics,
                SCROLL_TRACK,
                leftPos + VehicleManufacturingUiSpec.SCROLL_TRACK_X,
                topPos + VehicleManufacturingUiSpec.SCROLL_TRACK_Y,
                VehicleManufacturingUiSpec.SCROLL_TRACK_WIDTH,
                VehicleManufacturingUiSpec.SCROLL_TRACK_HEIGHT
        );
        int thumbY = VehicleManufacturingUiSpec.thumbY(scrollOffset, maxScroll());
        blitExact(
                graphics,
                SCROLL_THUMB,
                leftPos + VehicleManufacturingUiSpec.SCROLL_THUMB_X,
                topPos + thumbY,
                VehicleManufacturingUiSpec.SCROLL_THUMB_WIDTH,
                VehicleManufacturingUiSpec.SCROLL_THUMB_HEIGHT
        );
    }

    private void renderMaterials(GuiGraphics graphics, int mouseX, int mouseY) {
        double localMouseX = logicalLocalX(mouseX);
        double localMouseY = logicalLocalY(mouseY);
        menu.selectedRecipe().map(RecipeHolder::value).ifPresent(recipe -> {
            VehicleManufacturingMaterials.Match match = menu.materialMatch(recipe);
            for (int index = 0; index < recipe.materials().size(); index++) {
                SizedIngredient material = recipe.materials().get(index);
                int x = leftPos + VehicleManufacturingUiSpec.MATERIAL_SLOT_X[index];
                int y = topPos + VehicleManufacturingUiSpec.MATERIAL_SLOT_Y;
                boolean hovered = inside(
                        localMouseX,
                        localMouseY,
                        VehicleManufacturingUiSpec.MATERIAL_SLOT_X[index],
                        VehicleManufacturingUiSpec.MATERIAL_SLOT_Y,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE
                );
                blitExact(
                        graphics,
                        hovered ? SELECTED : UNSELECTED,
                        x,
                        y,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE
                );
                if (match.fulfilledCount(index) < material.count()) {
                    renderMaterialHighlight(graphics, x, y, MISSING_MATERIAL_OVERLAY);
                }
                ItemStack preview = materialPreview(material);
                if (!preview.isEmpty()) {
                    renderCompactItem(
                            graphics,
                            preview,
                            x,
                            y,
                            material.count() > 1 ? Integer.toString(material.count()) : null
                    );
                }
            }
        });
    }

    private static void renderMaterialHighlight(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(
                x + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                y + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                x + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET
                        + VehicleManufacturingUiSpec.COMPACT_ICON_SIZE,
                y + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET
                        + VehicleManufacturingUiSpec.COMPACT_ICON_SIZE,
                color
        );
    }

    private void renderCompactItem(
            GuiGraphics graphics,
            ItemStack stack,
            int slotX,
            int slotY,
            String countString
    ) {
        float scale = VehicleManufacturingUiSpec.COMPACT_ICON_SIZE / 16.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(
                slotX + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                slotY + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                0.0F
        );
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(font, stack, 0, 0, countString);
        graphics.pose().popPose();
    }

    private static void renderHotbarHighlight(
            GuiGraphics graphics,
            int itemX,
            int itemY,
            int color
    ) {
        graphics.fillGradient(
                RenderType.guiOverlay(),
                itemX + VehicleManufacturingUiSpec.HOTBAR_ICON_OFFSET,
                itemY + VehicleManufacturingUiSpec.HOTBAR_ICON_OFFSET,
                itemX + VehicleManufacturingUiSpec.HOTBAR_ICON_OFFSET
                        + VehicleManufacturingUiSpec.HOTBAR_ICON_SIZE,
                itemY + VehicleManufacturingUiSpec.HOTBAR_ICON_OFFSET
                        + VehicleManufacturingUiSpec.HOTBAR_ICON_SIZE,
                color,
                color,
                0
        );
    }

    private static void renderCompactHighlight(
            GuiGraphics graphics,
            int itemX,
            int itemY,
            int color
    ) {
        graphics.fillGradient(
                RenderType.guiOverlay(),
                itemX + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                itemY + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET,
                itemX + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET
                        + VehicleManufacturingUiSpec.COMPACT_ICON_SIZE,
                itemY + VehicleManufacturingUiSpec.COMPACT_ICON_OFFSET
                        + VehicleManufacturingUiSpec.COMPACT_ICON_SIZE,
                color,
                color,
                0
        );
    }

    private void renderCustomTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double localX = logicalLocalX(mouseX);
        double localY = logicalLocalY(mouseY);
        List<RecipeHolder<VehicleManufacturingRecipe>> recipes = menu.visibleRecipes();
        int recipeIndex = VehicleManufacturingUiSpec.recipeIndexAt(
                localX,
                localY,
                scrollOffset,
                recipes.size()
        );
        if (recipeIndex >= 0) {
            ItemStack result = recipes.get(recipeIndex).value().getResultItem(menuPlayer().registryAccess());
            graphics.renderTooltip(font, result, mouseX, mouseY);
            return;
        }
        menu.selectedRecipe().map(RecipeHolder::value).ifPresent(recipe -> {
            VehicleManufacturingMaterials.Match match = menu.materialMatch(recipe);
            for (int index = 0; index < recipe.materials().size(); index++) {
                int x = VehicleManufacturingUiSpec.MATERIAL_SLOT_X[index];
                int y = VehicleManufacturingUiSpec.MATERIAL_SLOT_Y;
                if (!inside(
                        localX,
                        localY,
                        x,
                        y,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE,
                        VehicleManufacturingUiSpec.LIST_SLOT_SIZE
                )) {
                    continue;
                }
                SizedIngredient material = recipe.materials().get(index);
                ItemStack preview = materialPreview(material);
                if (preview.isEmpty()) {
                    continue;
                }
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, preview));
                lines.add(Component.translatable(
                        "screen.polaris_vehicle.vehicle_manufacturing_table.material_count",
                        match.fulfilledCount(index),
                        material.count()
                ).withStyle(match.fulfilledCount(index) >= material.count()
                        ? ChatFormatting.GREEN
                        : ChatFormatting.RED));
                graphics.renderTooltip(
                        font,
                        lines,
                        preview.getTooltipImage(),
                        preview,
                        mouseX,
                        mouseY
                );
                return;
            }
        });
    }

    private PlayerView menuPlayer() {
        if (minecraft.player == null) {
            throw new IllegalStateException("Manufacturing screen rendered without a local player");
        }
        return new PlayerView(minecraft.player);
    }

    private void sendButton(int buttonId) {
        Pathway.nullable(minecraft.gameMode)
                .peek(mode -> mode.handleInventoryButtonClick(menu.containerId, buttonId));
    }

    private void updateScrollFromMouse(double localMouseY) {
        int maxScroll = maxScroll();
        double thumbY = localMouseY - scrollbarGrabOffset;
        scrollOffset = VehicleManufacturingUiSpec.scrollFromThumbY(thumbY, maxScroll);
    }

    private int maxScroll() {
        return VehicleManufacturingUiSpec.maxScroll(menu.visibleRecipes().size());
    }

    private void clampScroll() {
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    private void updateVehiclePreview() {
        if (minecraft.player == null) {
            vehiclePreview.clear();
            return;
        }
        menu.selectedRecipe().fold(
                () -> {
                    vehiclePreview.clear();
                    return false;
                },
                holder -> {
                    ItemStack result = holder.value().getResultItem(menuPlayer().registryAccess());
                    if (result.getItem() instanceof VehicleItem vehicleItem) {
                        vehiclePreview.select(
                                Maybe.some(vehicleItem.definition().id()),
                                minecraft.player.level()
                        );
                    } else {
                        vehiclePreview.selectItem(result);
                    }
                    return true;
                }
        );
    }

    private double logicalLocalX(double mouseX) {
        return mouseX - leftPos;
    }

    private double logicalLocalY(double mouseY) {
        return mouseY - topPos;
    }

    private static ItemStack materialPreview(SizedIngredient ingredient) {
        ItemStack[] options = ingredient.getItems();
        return options.length == 0 ? ItemStack.EMPTY : options[0].copy();
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static ResourceLocation texture(String name) {
        return PolarisVehicle.id(TEXTURE_ROOT + name);
    }

    private static void blitExact(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height
    ) {
        graphics.blit(texture, x, y, 0.0F, 0.0F, width, height, width, height);
    }

    private record PlayerView(LocalPlayer player) {
        private HolderLookup.Provider registryAccess() {
            return player.registryAccess();
        }
    }
}
