package com.flechazo.polaris_vehicle.manufacturing;

import com.flechazo.polaris_vehicle.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Comparator;

public final class VehicleManufacturingDisplayOrder {
    private VehicleManufacturingDisplayOrder() {
    }

    public static Comparator<RecipeHolder<VehicleManufacturingRecipe>> forCategory(
            VehicleManufacturingCategory category
    ) {
        Comparator<RecipeHolder<VehicleManufacturingRecipe>> recipeIdOrder =
                Comparator.comparing(holder -> holder.id().toString());
        if (category == VehicleManufacturingCategory.OTHER) {
            return Comparator.<RecipeHolder<VehicleManufacturingRecipe>>comparingInt(holder ->
                            holder.value().result().is(ModItems.CREW_DOLL_YELLOW_DUCK.get()) ? 1 : 0)
                    .thenComparing(recipeIdOrder);
        }
        return Comparator.<RecipeHolder<VehicleManufacturingRecipe>>comparingInt(holder -> itemRank(category,
                        BuiltInRegistries.ITEM.getKey(holder.value().result().getItem())))
                .thenComparing(recipeIdOrder);
    }

    static int itemRank(VehicleManufacturingCategory category, ResourceLocation item) {
        if (!item.getNamespace().equals("polaris_vehicle")) {
            return 100;
        }
        return switch (category) {
            case VEHICLE -> vehicleDisplayOrder(item.getPath());
            case TOOL -> toolDisplayOrder(item.getPath());
            case PAINT -> paintDisplayOrder(item.getPath());
            case PART -> partDisplayOrder(item.getPath());
            case TIRE -> tireDisplayOrder(item.getPath());
            case BLOCK -> blockDisplayOrder(item.getPath());
            case OTHER -> 0;
        };
    }

    private static int vehicleDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "elderly_car_vehicle" -> 0;
            case "small_car_vehicle" -> 1;
            case "two_seat_car_vehicle" -> 2;
            case "kart_vehicle" -> 10;
            case "convertible_vehicle" -> 11;
            case "rally_car_vehicle" -> 12;
            case "suv_vehicle" -> 13;
            case "small_bus_vehicle" -> 14;
            case "formula_car_vehicle" -> 15;
            default -> 100;
        };
    }

    private static int toolDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "wrench" -> 0;
            case "hammer" -> 1;
            case "electric_wrench" -> 2;
            case "portable_jack" -> 3;
            case "vehicle_key" -> 4;
            case "small_fuel_tank" -> 5;
            case "large_fuel_tank" -> 6;
            default -> 100;
        };
    }

    private static int paintDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "white_vehicle_paint" -> 0;
            case "black_vehicle_paint" -> 1;
            case "blue_vehicle_paint" -> 2;
            case "pink_vehicle_paint" -> 3;
            case "green_vehicle_paint" -> 4;
            case "red_vehicle_paint" -> 5;
            case "bread_vehicle_paint" -> 6;
            default -> 100;
        };
    }

    private static int partDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "alloy_ingot" -> 0;
            case "compressed_alloy_plate" -> 1;
            case "slime_rubber" -> 2;
            case "small_engine" -> 10;
            case "medium_engine" -> 11;
            case "large_engine" -> 12;
            case "drive_motor" -> 13;
            case "racing_engine_type_v" -> 20;
            case "racing_engine_type_a" -> 21;
            case "super_racing_engine_type_v" -> 22;
            case "super_racing_engine_type_a" -> 23;
            case "extra_power_unit_a" -> 30;
            case "extra_power_unit_b" -> 31;
            case "energy_recovery_unit_a" -> 32;
            case "energy_recovery_unit_b" -> 33;
            default -> 100;
        };
    }

    private static int tireDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "road_tire" -> 0;
            case "all_terrain_tire" -> 1;
            case "run_flat_tire" -> 2;
            case "racing_speed_tire" -> 10;
            case "racing_balanced_tire" -> 11;
            case "racing_endurance_tire" -> 12;
            default -> 100;
        };
    }

    private static int blockDisplayOrder(String itemPath) {
        return switch (itemPath) {
            case "vehicle_manufacturing_table" -> 0;
            case "assembly_table" -> 1;
            case "fuel_extractor" -> 2;
            case "roadblock" -> 3;
            case "asphalt_road" -> 10;
            case "asphalt_ramp" -> 11;
            case "upper_asphalt_ramp" -> 12;
            case "full_asphalt_ramp" -> 13;
            case "asphalt_third_ramp_1" -> 14;
            case "asphalt_third_ramp_2" -> 15;
            case "asphalt_third_ramp_3" -> 16;
            case "asphalt_plain_third_ramp_1" -> 17;
            case "asphalt_plain_third_ramp_2" -> 18;
            case "asphalt_plain_third_ramp_3" -> 19;
            case "asphalt_road_line" -> 20;
            case "asphalt_line_ramp" -> 21;
            case "upper_asphalt_line_ramp" -> 22;
            case "full_asphalt_line_ramp" -> 23;
            case "concrete_road" -> 30;
            case "concrete_ramp" -> 31;
            case "upper_concrete_ramp" -> 32;
            case "full_concrete_ramp" -> 33;
            case "concrete_third_ramp_1" -> 34;
            case "concrete_third_ramp_2" -> 35;
            case "concrete_third_ramp_3" -> 36;
            case "concrete_plain_third_ramp_1" -> 37;
            case "concrete_plain_third_ramp_2" -> 38;
            case "concrete_plain_third_ramp_3" -> 39;
            case "concrete_road_line" -> 40;
            case "concrete_line_ramp" -> 41;
            case "upper_concrete_line_ramp" -> 42;
            case "full_concrete_line_ramp" -> 43;
            case "mimic_ramp" -> 50;
            case "upper_mimic_ramp" -> 51;
            case "full_mimic_ramp" -> 52;
            case "mimic_third_ramp_1" -> 53;
            case "mimic_third_ramp_2" -> 54;
            case "mimic_third_ramp_3" -> 55;
            case "black_white_block" -> 60;
            default -> 100;
        };
    }
}
