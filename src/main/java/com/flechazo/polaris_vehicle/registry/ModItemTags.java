package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class ModItemTags {

    public static final TagKey<Item> VEHICLE_TOOLS = TagKey.create(Registries.ITEM,
            PolarisVehicle.id("vehicle_tools"));

    public static boolean holdsVehicleTool(Player player) {
        return player.getMainHandItem().is(VEHICLE_TOOLS) || player.getOffhandItem().is(VEHICLE_TOOLS);
    }
}
