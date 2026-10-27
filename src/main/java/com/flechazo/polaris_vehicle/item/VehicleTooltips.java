package com.flechazo.polaris_vehicle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class VehicleTooltips {
    public static final String INDENT = "  ";

    public static boolean details(List<Component> tooltip, TooltipFlag flag) {
        if (flag.hasShiftDown()) return true;
        tooltip.add(Component.translatable("tooltip.polaris_vehicle.hold_shift",
                Component.literal("Shift").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY));
        return false;
    }
    public static MutableComponent heading(Component text) {
        return text.copy().withStyle(ChatFormatting.DARK_AQUA);
    }

    public static MutableComponent item(Component text, ChatFormatting colour) {
        return Component.literal(INDENT).append(text).withStyle(colour);
    }
}
