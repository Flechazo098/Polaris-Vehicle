package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.menu.AssemblyTableMenu;
import com.flechazo.polaris_vehicle.content.menu.AssemblyTableUiSlot;
import com.flechazo.polaris_vehicle.item.VehiclePartItem;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePartCompatibility;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePerformanceCalculator;
import com.flechazo.polaris_vehicle.vehicle.physics.VehicleWheelTerrain;
import com.flechazo.polaris_vehicle.vehicle.physics.WheelTerrain;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public final class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {
    private static final String TEXTURE_ROOT = "textures/gui/container/assembly_table/";
    private static final ResourceLocation BACKGROUND = texture("background.png");
    private static final int PARAMETER_X = 228;
    private static final int PARAMETER_Y = 16;
    private static final int PARAMETER_WIDTH = 107;
    private static final int PART_DETAILS_Y = 132;
    private static final int PARAMETER_TEXT = 0xFF25302C;
    private static final int PARAMETER_MUTED = 0xFF4B5752;
    private static final int DISABLED_OVERLAY = 0x77404747;
    private static final int HOVER_OVERLAY = 0x55FFFFFF;
    private final VehicleGuiPreview vehiclePreview = VehicleGuiPreview.assembly();
    private AssemblyTableUiSlot hoveredUiSlot;

    public AssemblyTableScreen(
            AssemblyTableMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = AssemblyTableMenu.IMAGE_WIDTH;
        imageHeight = AssemblyTableMenu.IMAGE_HEIGHT;
        titleLabelX = 0;
        titleLabelY = -1000;
        inventoryLabelX = 0;
        inventoryLabelY = -1000;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderAssemblyTooltip(graphics, mouseX, mouseY);
        renderPerformanceTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        blitExact(graphics, BACKGROUND, leftPos, topPos, imageWidth, imageHeight);
        if (minecraft.player != null) {
            vehiclePreview.update(menu.vehicleData(), minecraft.player.level());
            vehiclePreview.render(graphics, leftPos, topPos, partialTick);
        }

        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        hoveredUiSlot = null;
        for (AssemblyTableUiSlot uiSlot : AssemblyTableUiSlot.values()) {
            if (!uiSlot.isVisibleFor(menu.vehicleDefinition())) {
                continue;
            }
            renderAssemblySlot(graphics, uiSlot, localX, localY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        renderParameters(graphics);
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
        int color = getSlotColor(menuIndex);
        if (menuIndex < menu.managedSlots().size()) {
            renderIrregularHighlight(graphics, slot.x, slot.y, color);
            return;
        }
        if (menuIndex >= menu.playerHotbarStart()) {
            renderHotbarHighlight(graphics, slot.x, slot.y, color);
            return;
        }
        renderCompactHighlight(graphics, slot.x, slot.y, color);
    }

    @Override
    protected void renderSlotContents(
            GuiGraphics graphics,
            ItemStack stack,
            Slot slot,
            String countString
    ) {
        int menuIndex = menu.slots.indexOf(slot);
        if (menuIndex < menu.managedSlots().size()) {
            super.renderSlotContents(graphics, stack, slot, countString);
            return;
        }
        boolean hotbar = menuIndex >= menu.playerHotbarStart();
        int size = hotbar ? 15 : 11;
        int offset = hotbar ? 1 : 3;
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
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        if (vehiclePreview.beginDrag(localX, localY, button)) {
            return true;
        }
        if (button == 0) {
            for (AssemblyTableUiSlot uiSlot : AssemblyTableUiSlot.values()) {
                if (!uiSlot.isVisibleFor(menu.vehicleDefinition())) {
                    continue;
                }
                if (!inside(localX, localY, uiSlot.x(), uiSlot.y(),
                        AssemblyTableUiSlot.FRAME_SIZE, AssemblyTableUiSlot.FRAME_SIZE)) {
                    continue;
                }
                return uiSlot.partSlot().fold(
                        () -> showUnavailableSlot(uiSlot),
                        partSlot -> clickPartSlot(uiSlot, partSlot)
                );
            }
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
        if (vehiclePreview.drag(button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return vehiclePreview.endDrag(button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (vehiclePreview.scroll(mouseX - leftPos, mouseY - topPos, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        vehiclePreview.clear();
        super.removed();
    }

    private void renderAssemblySlot(
            GuiGraphics graphics,
            AssemblyTableUiSlot uiSlot,
            double mouseX,
            double mouseY
    ) {
        int x = leftPos + uiSlot.x();
        int y = topPos + uiSlot.y();
        blitExact(
                graphics,
                texture(uiSlot.textureName() + ".png"),
                x,
                y,
                AssemblyTableUiSlot.FRAME_SIZE,
                AssemblyTableUiSlot.FRAME_SIZE
        );

        boolean implemented = uiSlot.isImplementedFor(menu.vehicleDefinition());
        if (!implemented) {
            graphics.fill(x + 2, y + 2, x + 28, y + 28, DISABLED_OVERLAY);
        }
        if (inside(mouseX, mouseY, uiSlot.x(), uiSlot.y(),
                AssemblyTableUiSlot.FRAME_SIZE, AssemblyTableUiSlot.FRAME_SIZE)) {
            hoveredUiSlot = uiSlot;
            renderIrregularFrameHighlight(graphics, x, y, HOVER_OVERLAY);
        }
    }

    private void renderParameters(GuiGraphics graphics) {
        VehicleDefinition definition = menu.vehicleDefinition();
        VehicleInstanceData data = menu.vehicleData();
        AssemblyVehicleStats stats = AssemblyVehicleStats.calculate(definition, data);
        int wheels = 0;
        for (VehiclePartSlot wheelSlot : definition.wheelSlots()) {
            if (data.part(wheelSlot).isDefined()) {
                wheels++;
            }
        }

        int x = PARAMETER_X + 6;
        int y = PARAMETER_Y + 6;
        int line = 13;
        Component vehicleName = Component.translatable(
                "entity." + definition.id().getNamespace() + "." + definition.id().getPath()
        );
        graphics.drawString(font, fit(vehicleName, PARAMETER_WIDTH - 12), x, y,
                PARAMETER_TEXT, false);
        y += line + 1;
        drawParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.weight",
                format(definition.bodyWeight()) + " kg");
        y += line;
        drawParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.horsepower",
                format(stats.horsepower()));
        y += line;
        drawPerformanceParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.max_speed",
                stats.baseSpeed(), stats.tireSpeed(), "m/s");
        y += line;
        drawPerformanceParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.acceleration",
                stats.baseAcceleration(), stats.tireAcceleration(), "m/s²");
        y += line;
        drawParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.fuel",
                data.fuel() + "/" + definition.fuelCapacity() + " ml");
        y += line;
        drawParameter(graphics, x, y, "screen.polaris_vehicle.assembly_table.wheels",
                wheels + "/" + definition.wheelSlots().size());

        // renderPowerPartDetails(graphics, data);
    }

    private void renderPowerPartDetails(GuiGraphics graphics, VehicleInstanceData data) {
        Maybe<PowerPartDetails> extra = powerPartDetails(
                data,
                VehiclePartSlot.EXTRA_POWER_UNIT
        );
        Maybe<PowerPartDetails> recovery = powerPartDetails(
                data,
                VehiclePartSlot.ENERGY_RECOVERY_UNIT
        );
        if (extra.isEmpty() && recovery.isEmpty()) {
            return;
        }

        int x = PARAMETER_X + 6;
        int y = PART_DETAILS_Y;
        graphics.drawString(
                font,
                Component.translatable("screen.polaris_vehicle.assembly_table.part_details"),
                x,
                y,
                PARAMETER_TEXT,
                false
        );
        y += 10;
        int extraY = y;
        y = extra.fold(
                () -> extraY,
                details -> renderExtraPowerDetails(graphics, x, extraY, details)
        );
        if (extra.isDefined() && recovery.isDefined()) {
            y += 3;
        }
        int recoveryY = y;
        recovery.ifPresent(details -> renderRecoveryDetails(
                graphics,
                x,
                recoveryY,
                details
        ));
    }

    private int renderExtraPowerDetails(
            GuiGraphics graphics,
            int x,
            int y,
            PowerPartDetails details
    ) {
        VehiclePowerDefinition definition = details.definition();
        y = drawDetailHeading(
                graphics,
                x,
                y,
                "screen.polaris_vehicle.assembly_table.extra_power_detail"
        );
        y = drawDetail(graphics, x, y, "screen.polaris_vehicle.assembly_table.detail.model",
                VehiclePartItem.stackFor(details.part()).getHoverName().getString());
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.normal_speed",
                signed(definition.normalMaxSpeedModifier()) + " m/s");
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.normal_acceleration",
                signed(definition.normalAccelerationModifier()) + " m/s²");
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.energy_capacity",
                Integer.toString(definition.energyCapacity()));
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.energy_cost",
                Integer.toString(definition.energyCost()));
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.duration",
                seconds(definition.boostDurationTicks()));
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.cooldown",
                seconds(definition.cooldownTicks()));
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.boost_speed",
                signed(definition.boostMaxSpeedModifier()) + " m/s");
        return drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.boost_acceleration",
                signed(definition.boostAccelerationModifier()) + " m/s²");
    }

    private int renderRecoveryDetails(
            GuiGraphics graphics,
            int x,
            int y,
            PowerPartDetails details
    ) {
        VehiclePowerDefinition definition = details.definition();
        y = drawDetailHeading(
                graphics,
                x,
                y,
                "screen.polaris_vehicle.assembly_table.recovery_detail"
        );
        y = drawDetail(graphics, x, y, "screen.polaris_vehicle.assembly_table.detail.model",
                VehiclePartItem.stackFor(details.part()).getHoverName().getString());
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.normal_speed",
                signed(definition.normalMaxSpeedModifier()) + " m/s");
        y = drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.normal_acceleration",
                signed(definition.normalAccelerationModifier()) + " m/s²");
        return drawDetail(graphics, x, y,
                "screen.polaris_vehicle.assembly_table.detail.passive_charge",
                signedTwoDecimals(definition.passiveChargePerSecond()) + "/s");
    }

    private int drawDetailHeading(
            GuiGraphics graphics,
            int x,
            int y,
            String translationKey
    ) {
        graphics.drawString(
                font,
                fit(Component.translatable(translationKey), PARAMETER_WIDTH - 12),
                x,
                y,
                PARAMETER_TEXT,
                false
        );
        return y + 9;
    }

    private int drawDetail(
            GuiGraphics graphics,
            int x,
            int y,
            String translationKey,
            String value
    ) {
        Component text = Component.translatable(translationKey, value);
        graphics.drawString(
                font,
                fit(text, PARAMETER_WIDTH - 12),
                x,
                y,
                PARAMETER_MUTED,
                false
        );
        return y + 9;
    }

    private static Maybe<PowerPartDetails> powerPartDetails(
            VehicleInstanceData data,
            VehiclePartSlot slot
    ) {
        return data.part(slot).flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                .filter(definition -> definition.matches(part))
                .map(definition -> new PowerPartDetails(part, definition)));
    }

    private void drawParameter(
            GuiGraphics graphics,
            int x,
            int y,
            String translationKey,
            String value
    ) {
        Component label = Component.translatable(translationKey);
        Component line = Component.literal(label.getString() + ": " + value);
        graphics.drawString(font, fit(line, PARAMETER_WIDTH - 12), x, y,
                PARAMETER_MUTED, false);
    }

    private Component fit(Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        return Component.literal(font.substrByWidth(
                text,
                Math.max(0, width - font.width(ellipsis))
        ).getString() + ellipsis);
    }

    private void drawPerformanceParameter(GuiGraphics graphics, int x, int y, String key,
                                          double base, double tire, String unit) {
        Component text = Component.literal(Component.translatable(key).getString() + ": "
                + format(base) + signed(tire) + " " + unit);
        float scale = Math.min(1.0F, (PARAMETER_WIDTH - 12.0F) / font.width(text));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (font.lineHeight * (1 - scale) / 2), 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, PARAMETER_MUTED, false);
        graphics.pose().popPose();
    }

    private void renderPerformanceTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = PARAMETER_X + 6, speedY = PARAMETER_Y + 6 + 14 + 13 * 2;
        double localX = mouseX - leftPos, localY = mouseY - topPos;
        boolean speed = inside(localX, localY, x, speedY, PARAMETER_WIDTH - 12, 13);
        boolean acceleration = inside(localX, localY, x, speedY + 13, PARAMETER_WIDTH - 12, 13);
        if (!speed && !acceleration) return;
        var stats = AssemblyVehicleStats.calculate(menu.vehicleDefinition(), menu.vehicleData());
        double base = speed ? stats.baseSpeed() : stats.baseAcceleration();
        double tire = speed ? stats.tireSpeed() : stats.tireAcceleration();
        String unit = speed ? "m/s" : "m/s²";
        if (acceleration) {
            var detail = VehiclePerformanceCalculator.accelerationBreakdown(
                    menu.vehicleDefinition(), menu.vehicleData(),
                    VehicleWheelTerrain.uniform(
                            WheelTerrain.ROAD,
                            menu.vehicleDefinition().wheelSlots().size()));
            var lines = new ArrayList<FormattedCharSequence>();
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_engine",
                    format(detail.engine()) + " " + unit).getVisualOrderText());
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_tires",
                    signed(detail.tires()) + " " + unit).getVisualOrderText());
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_fuel",
                    signed(detail.fuel()) + " " + unit).getVisualOrderText());
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_power",
                    signed(detail.power()) + " " + unit).getVisualOrderText());
            if (detail.tuning() != 0 || detail.baseAdjustment() != 0) {
                lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_tuning",
                        signed(detail.tuning() + detail.baseAdjustment()) + " " + unit).getVisualOrderText());
            }
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_total",
                    format(detail.total()) + " " + unit).getVisualOrderText());
            lines.add(Component.translatable("screen.polaris_vehicle.assembly_table.performance_road_hint")
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            graphics.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
        graphics.renderTooltip(font, List.of(
                Component.translatable("screen.polaris_vehicle.assembly_table.performance_base", format(base) + " " + unit).getVisualOrderText(),
                Component.translatable("screen.polaris_vehicle.assembly_table.performance_tires", signed(tire) + " " + unit).getVisualOrderText(),
                Component.translatable("screen.polaris_vehicle.assembly_table.performance_total", format(base + tire) + " " + unit).getVisualOrderText(),
                Component.translatable("screen.polaris_vehicle.assembly_table.performance_road_hint").withStyle(ChatFormatting.GRAY).getVisualOrderText()
        ), mouseX, mouseY);
    }

    private boolean clickPartSlot(AssemblyTableUiSlot uiSlot, VehiclePartSlot partSlot) {
        if (!uiSlot.isImplementedFor(menu.vehicleDefinition())) {
            return showUnavailableSlot(uiSlot);
        }
        boolean occupied = !menu.installedStack(partSlot).isEmpty();
        int buttonId;
        if (!occupied) {
            buttonId = AssemblyTableMenu.installButtonId(partSlot);
        } else if (hasShiftDown()) {
            buttonId = AssemblyTableMenu.quickRemoveButtonId(partSlot);
        } else {
            buttonId = AssemblyTableMenu.removeButtonId(partSlot);
        }
        sendButton(buttonId);
        return true;
    }

    private boolean showUnavailableSlot(AssemblyTableUiSlot slot) {
        if (minecraft.player != null) {
            Component slotName = Component.translatable(slot.translationKey());
            Component message = slot.partSlot().fold(
                    () -> Component.translatable(
                            "screen.polaris_vehicle.assembly_table.future_slot", slotName),
                    ignored -> Component.translatable(
                            "screen.polaris_vehicle.assembly_table.unsupported_slot", slotName)
            );
            minecraft.player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private void renderAssemblyTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredUiSlot == null) {
            return;
        }
        Component name = Component.translatable(hoveredUiSlot.translationKey());
        if (hoveredUiSlot.isImplementedFor(menu.vehicleDefinition())) {
            hoveredUiSlot.partSlot().ifPresent(partSlot -> {
                if (menu.installedStack(partSlot).isEmpty()) {
                    graphics.renderTooltip(
                            font,
                            compatiblePartTooltip(name, partSlot),
                            mouseX,
                            mouseY
                    );
                }
            });
            return;
        }
        Component hint = hoveredUiSlot.partSlot().fold(
                () -> Component.translatable(
                        "screen.polaris_vehicle.assembly_table.future_slot_hint"),
                ignored -> Component.translatable(
                        "screen.polaris_vehicle.assembly_table.unsupported_slot_hint")
        );
        graphics.renderTooltip(font, List.of(
                name.getVisualOrderText(),
                hint.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText()
        ), mouseX, mouseY);
    }

    private List<FormattedCharSequence> compatiblePartTooltip(
            Component slotName,
            VehiclePartSlot partSlot
    ) {
        List<ResourceLocation> compatible = VehiclePartCompatibility.compatiblePartIds(
                menu.vehicleDefinition(),
                partSlot
        );
        List<FormattedCharSequence> lines = new ArrayList<>(compatible.size() + 3);
        lines.add(slotName.getVisualOrderText());
        if (compatible.isEmpty()) {
            lines.add(Component.translatable(
                    "screen.polaris_vehicle.assembly_table.no_compatible_parts"
            ).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            return List.copyOf(lines);
        }
        lines.add(Component.translatable(
                "screen.polaris_vehicle.assembly_table.compatible_parts"
        ).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        compatible.stream()
                .map(AssemblyTableScreen::partName)
                .map(partName -> Component.literal("• ").append(partName)
                        .withStyle(ChatFormatting.WHITE).getVisualOrderText())
                .forEach(lines::add);
        lines.add(Component.translatable(
                "screen.polaris_vehicle.assembly_table.install_hint"
        ).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return List.copyOf(lines);
    }

    private static Component partName(ResourceLocation partId) {
        return Component.translatable(
                "item." + partId.getNamespace() + "." + partId.getPath()
        );
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private static void renderIrregularHighlight(
            GuiGraphics graphics,
            int itemX,
            int itemY,
            int color
    ) {
        graphics.fill(itemX - 2, itemY - 1, itemX + 18, itemY + 17, color);
        graphics.fill(itemX - 1, itemY - 2, itemX + 17, itemY - 1, color);
        graphics.fill(itemX - 1, itemY + 17, itemX + 17, itemY + 18, color);
    }

    private static void renderIrregularFrameHighlight(
            GuiGraphics graphics,
            int frameX,
            int frameY,
            int color
    ) {
        renderIrregularHighlight(graphics, frameX + AssemblyTableUiSlot.ITEM_OFFSET,
                frameY + AssemblyTableUiSlot.ITEM_OFFSET, color);
    }

    private static void renderCompactHighlight(
            GuiGraphics graphics,
            int itemX,
            int itemY,
            int color
    ) {
        graphics.fillGradient(
                RenderType.guiOverlay(),
                itemX + 3,
                itemY + 3,
                itemX + 14,
                itemY + 14,
                color,
                color,
                0
        );
    }

    private static void renderHotbarHighlight(
            GuiGraphics graphics,
            int itemX,
            int itemY,
            int color
    ) {
        graphics.fillGradient(
                RenderType.guiOverlay(),
                itemX + 1,
                itemY + 1,
                itemX + 16,
                itemY + 16,
                color,
                color,
                0
        );
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    private static String signedTwoDecimals(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
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

    private record PowerPartDetails(
            InstalledPart part,
            VehiclePowerDefinition definition
    ) {
    }
}
