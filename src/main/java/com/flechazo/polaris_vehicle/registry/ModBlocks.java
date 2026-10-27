package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.block.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PolarisVehicle.MODID);

    public static final DeferredBlock<RampBlock> MIMIC_RAMP = BLOCKS.registerBlock(
            "mimic_ramp",
            RampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<UpperRampBlock> UPPER_MIMIC_RAMP = BLOCKS.registerBlock(
            "upper_mimic_ramp",
            UpperRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<FullRampBlock> FULL_MIMIC_RAMP = BLOCKS.registerBlock(
            "full_mimic_ramp",
            FullRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<RampBlock> ASPHALT_RAMP = BLOCKS.registerBlock(
            "asphalt_ramp", RampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<UpperRampBlock> UPPER_ASPHALT_RAMP = BLOCKS.registerBlock(
            "upper_asphalt_ramp", UpperRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<FullRampBlock> FULL_ASPHALT_RAMP = BLOCKS.registerBlock(
            "full_asphalt_ramp", FullRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<RampBlock> CONCRETE_RAMP = BLOCKS.registerBlock(
            "concrete_ramp", RampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<UpperRampBlock> UPPER_CONCRETE_RAMP = BLOCKS.registerBlock(
            "upper_concrete_ramp", UpperRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<FullRampBlock> FULL_CONCRETE_RAMP = BLOCKS.registerBlock(
            "full_concrete_ramp", FullRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<RampBlock> ASPHALT_LINE_RAMP = BLOCKS.registerBlock(
            "asphalt_line_ramp", RampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<UpperRampBlock> UPPER_ASPHALT_LINE_RAMP = BLOCKS.registerBlock(
            "upper_asphalt_line_ramp", UpperRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<FullRampBlock> FULL_ASPHALT_LINE_RAMP = BLOCKS.registerBlock(
            "full_asphalt_line_ramp", FullRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion()
    );
    public static final DeferredBlock<RampBlock> CONCRETE_LINE_RAMP = BLOCKS.registerBlock(
            "concrete_line_ramp", RampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<UpperRampBlock> UPPER_CONCRETE_LINE_RAMP = BLOCKS.registerBlock(
            "upper_concrete_line_ramp", UpperRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<FullRampBlock> FULL_CONCRETE_LINE_RAMP = BLOCKS.registerBlock(
            "full_concrete_line_ramp", FullRampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion()
    );
    public static final DeferredBlock<ThirdRampBlock> MIMIC_THIRD_RAMP_1 = BLOCKS.registerBlock(
            "mimic_third_ramp_1", properties -> new ThirdRampBlock(properties, 0),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> MIMIC_THIRD_RAMP_2 = BLOCKS.registerBlock(
            "mimic_third_ramp_2", properties -> new ThirdRampBlock(properties, 1),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> MIMIC_THIRD_RAMP_3 = BLOCKS.registerBlock(
            "mimic_third_ramp_3", properties -> new ThirdRampBlock(properties, 2),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_THIRD_RAMP_1 = BLOCKS.registerBlock(
            "asphalt_third_ramp_1", properties -> new ThirdRampBlock(properties, 0),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_THIRD_RAMP_2 = BLOCKS.registerBlock(
            "asphalt_third_ramp_2", properties -> new ThirdRampBlock(properties, 1),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_THIRD_RAMP_3 = BLOCKS.registerBlock(
            "asphalt_third_ramp_3", properties -> new ThirdRampBlock(properties, 2),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_THIRD_RAMP_1 = BLOCKS.registerBlock(
            "concrete_third_ramp_1", properties -> new ThirdRampBlock(properties, 0),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_THIRD_RAMP_2 = BLOCKS.registerBlock(
            "concrete_third_ramp_2", properties -> new ThirdRampBlock(properties, 1),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_THIRD_RAMP_3 = BLOCKS.registerBlock(
            "concrete_third_ramp_3", properties -> new ThirdRampBlock(properties, 2),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_PLAIN_THIRD_RAMP_1 = BLOCKS.registerBlock(
            "asphalt_plain_third_ramp_1", properties -> new ThirdRampBlock(properties, 0),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_PLAIN_THIRD_RAMP_2 = BLOCKS.registerBlock(
            "asphalt_plain_third_ramp_2", properties -> new ThirdRampBlock(properties, 1),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> ASPHALT_PLAIN_THIRD_RAMP_3 = BLOCKS.registerBlock(
            "asphalt_plain_third_ramp_3", properties -> new ThirdRampBlock(properties, 2),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_PLAIN_THIRD_RAMP_1 = BLOCKS.registerBlock(
            "concrete_plain_third_ramp_1", properties -> new ThirdRampBlock(properties, 0),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_PLAIN_THIRD_RAMP_2 = BLOCKS.registerBlock(
            "concrete_plain_third_ramp_2", properties -> new ThirdRampBlock(properties, 1),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<ThirdRampBlock> CONCRETE_PLAIN_THIRD_RAMP_3 = BLOCKS.registerBlock(
            "concrete_plain_third_ramp_3", properties -> new ThirdRampBlock(properties, 2),
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion());
    public static final DeferredBlock<RoadblockBlock> ROADBLOCK = BLOCKS.registerBlock(
            "roadblock", RoadblockBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.YELLOW_CONCRETE).noOcclusion()
    );
    public static final DeferredBlock<Block> BLACK_WHITE_BLOCK = BLOCKS.registerSimpleBlock(
            "black_white_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)
    );
    public static final DeferredBlock<Block> ASPHALT_ROAD = BLOCKS.registerSimpleBlock(
            "asphalt_road",
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
    );
    public static final DeferredBlock<RoadLineBlock> ASPHALT_ROAD_LINE = BLOCKS.registerBlock(
            "asphalt_road_line",
            RoadLineBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
    );
    public static final DeferredBlock<Block> CONCRETE_ROAD = BLOCKS.registerSimpleBlock(
            "concrete_road",
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)
    );
    public static final DeferredBlock<RoadLineBlock> CONCRETE_ROAD_LINE = BLOCKS.registerBlock(
            "concrete_road_line",
            RoadLineBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)
    );
    public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = BLOCKS.registerBlock(
            "assembly_table",
            AssemblyTableBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0F).noOcclusion()
    );
    public static final DeferredBlock<FuelExtractorBlock> FUEL_EXTRACTOR = BLOCKS.registerBlock(
            "fuel_extractor",
            FuelExtractorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0F).noOcclusion()
    );
    public static final DeferredBlock<VehicleManufacturingTableBlock> VEHICLE_MANUFACTURING_TABLE =
            BLOCKS.registerBlock(
                    "vehicle_manufacturing_table",
                    VehicleManufacturingTableBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0F).noOcclusion()
            );

    public static List<DeferredBlock<? extends RampBlock>> fixedRamps() {
        return List.of(ASPHALT_RAMP, UPPER_ASPHALT_RAMP, FULL_ASPHALT_RAMP,
                CONCRETE_RAMP, UPPER_CONCRETE_RAMP, FULL_CONCRETE_RAMP,
                ASPHALT_LINE_RAMP, UPPER_ASPHALT_LINE_RAMP, FULL_ASPHALT_LINE_RAMP,
                CONCRETE_LINE_RAMP, UPPER_CONCRETE_LINE_RAMP, FULL_CONCRETE_LINE_RAMP,
                ASPHALT_THIRD_RAMP_1, ASPHALT_THIRD_RAMP_2, ASPHALT_THIRD_RAMP_3, CONCRETE_THIRD_RAMP_1, CONCRETE_THIRD_RAMP_2, CONCRETE_THIRD_RAMP_3,
                ASPHALT_PLAIN_THIRD_RAMP_1, ASPHALT_PLAIN_THIRD_RAMP_2, ASPHALT_PLAIN_THIRD_RAMP_3,
                CONCRETE_PLAIN_THIRD_RAMP_1, CONCRETE_PLAIN_THIRD_RAMP_2, CONCRETE_PLAIN_THIRD_RAMP_3);
    }

    public static List<RampBlock> allRamps() {
        List<RampBlock> ramps = new ArrayList<>();
        ramps.add(MIMIC_RAMP.get());
        ramps.add(UPPER_MIMIC_RAMP.get());
        ramps.add(FULL_MIMIC_RAMP.get());
        ramps.add(MIMIC_THIRD_RAMP_1.get());
        ramps.add(MIMIC_THIRD_RAMP_2.get());
        ramps.add(MIMIC_THIRD_RAMP_3.get());
        fixedRamps().forEach(ramp -> ramps.add(ramp.get()));
        return List.copyOf(ramps);
    }

    public static boolean isMimicRampId(ResourceLocation id) {
        return List.of(MIMIC_RAMP.getId(), UPPER_MIMIC_RAMP.getId(), FULL_MIMIC_RAMP.getId(),
                MIMIC_THIRD_RAMP_1.getId(), MIMIC_THIRD_RAMP_2.getId(), MIMIC_THIRD_RAMP_3.getId()).contains(id);
    }

    public static boolean isFixedRampId(ResourceLocation id) {
        return fixedRamps().stream().anyMatch(ramp -> ramp.getId().equals(id));
    }

    public static boolean matchingFixedRampMaterial(Block first, Block second) {
        ResourceLocation firstId = BuiltInRegistries.BLOCK.getKey(first);
        ResourceLocation secondId = BuiltInRegistries.BLOCK.getKey(second);
        return isFixedRampId(firstId) && isFixedRampId(secondId)
                && fixedRampMaterial(firstId).equals(fixedRampMaterial(secondId));
    }

    private static String fixedRampMaterial(ResourceLocation id) {
        return id.getPath().replaceFirst("^(upper_|full_)", "").replaceFirst("_third_ramp_[123]$", "_third_ramp");
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
