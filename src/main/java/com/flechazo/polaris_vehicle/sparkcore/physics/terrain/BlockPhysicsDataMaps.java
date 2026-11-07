// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.SparkCore;
import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public final class BlockPhysicsDataMaps {
    public static final DataMapType<Block, BlockPhysicsData> BLOCK_PHYSICS_DATA =
            DataMapType.builder(
                            ResourceLocation.fromNamespaceAndPath(
                                    SparkCore.MOD_ID, "block_physics"),
                            Registries.BLOCK,
                            BlockPhysicsData.CODEC)
                    .build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(BLOCK_PHYSICS_DATA);
    }

    public static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) BlockCollisionUtil.invalidatePhysicsCache();
    }
}
