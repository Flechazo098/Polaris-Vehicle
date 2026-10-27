package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> ROAD_BLOCKS = create("road_blocks");
    public static final TagKey<Block> SOFT_GROUND_BLOCKS = create("soft_ground_blocks");
    public static final TagKey<Block> RACING_TIRE_GRASS_BLOCKS = create("racing_tire_grass_blocks");
    public static final TagKey<Block> RACING_TIRE_SAND_BLOCKS = create("racing_tire_sand_blocks");
    public static final TagKey<Block> RACING_TIRE_GRAVEL_BLOCKS = create("racing_tire_gravel_blocks");

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, PolarisVehicle.id(path));
    }
}
