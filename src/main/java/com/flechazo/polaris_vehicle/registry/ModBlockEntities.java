package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.content.block.entity.FuelExtractorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PolarisVehicle.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE =
            BLOCK_ENTITIES.register("assembly_table", () ->
                    BlockEntityType.Builder.of(
                            AssemblyTableBlockEntity::new,
                            ModBlocks.ASSEMBLY_TABLE.get()
                    ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelExtractorBlockEntity>> FUEL_EXTRACTOR =
            BLOCK_ENTITIES.register("fuel_extractor", () ->
                    BlockEntityType.Builder.of(
                            FuelExtractorBlockEntity::new,
                            ModBlocks.FUEL_EXTRACTOR.get()
                    ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
