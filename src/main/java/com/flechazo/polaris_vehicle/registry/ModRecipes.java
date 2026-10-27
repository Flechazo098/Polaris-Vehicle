package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.fuel.FuelExtractorRecipe;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(
            Registries.RECIPE_TYPE,
            PolarisVehicle.MODID
    );
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(
            Registries.RECIPE_SERIALIZER,
            PolarisVehicle.MODID
    );

    public static final DeferredHolder<RecipeType<?>, RecipeType<FuelExtractorRecipe>> FUEL_EXTRACTION_TYPE =
            RECIPE_TYPES.register(
                    "fuel_extraction",
                    () -> RecipeType.simple(PolarisVehicle.id("fuel_extraction"
                    ))
            );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FuelExtractorRecipe>>
            FUEL_EXTRACTION_SERIALIZER = RECIPE_SERIALIZERS.register(
            "fuel_extraction",
            FuelExtractorRecipe.Serializer::new
    );

    public static final DeferredHolder<RecipeType<?>, RecipeType<VehicleManufacturingRecipe>>
            VEHICLE_MANUFACTURING_TYPE = RECIPE_TYPES.register(
            "vehicle_manufacturing",
            () -> RecipeType.simple(PolarisVehicle.id("vehicle_manufacturing"
            ))
    );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VehicleManufacturingRecipe>>
            VEHICLE_MANUFACTURING_SERIALIZER = RECIPE_SERIALIZERS.register(
            "vehicle_manufacturing",
            VehicleManufacturingRecipe.Serializer::new
    );

    public static void register(IEventBus eventBus) {
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
