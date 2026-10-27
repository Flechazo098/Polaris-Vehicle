package com.flechazo.polaris_vehicle.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class VehicleKeyItem extends StaticGeoItem {
    public VehicleKeyItem(Properties properties) {
        super(properties, "vehicle_key_model");
    }

    @Override
    public boolean usesAuthoredHandDisplay() {
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        if (!VehicleTooltips.details(tooltip, flag)) return;
        tooltip.add(Component.translatable("tooltip.polaris_vehicle.vehicle_key")
                .withStyle(ChatFormatting.GRAY));
    }
}
