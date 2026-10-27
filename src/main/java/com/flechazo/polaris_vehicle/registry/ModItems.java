package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.*;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinition;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinitions;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinition;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinitions;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PolarisVehicle.MODID);

    public static final DeferredItem<BlockItem> MIMIC_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.MIMIC_RAMP);
    public static final DeferredItem<BlockItem> UPPER_MIMIC_RAMP =
            ITEMS.registerSimpleBlockItem(ModBlocks.UPPER_MIMIC_RAMP);
    public static final DeferredItem<BlockItem> FULL_MIMIC_RAMP =
            ITEMS.registerSimpleBlockItem(ModBlocks.FULL_MIMIC_RAMP);
    public static final DeferredItem<BlockItem> MIMIC_THIRD_RAMP_1 = ITEMS.registerSimpleBlockItem(ModBlocks.MIMIC_THIRD_RAMP_1);
    public static final DeferredItem<BlockItem> MIMIC_THIRD_RAMP_2 = ITEMS.registerSimpleBlockItem(ModBlocks.MIMIC_THIRD_RAMP_2);
    public static final DeferredItem<BlockItem> MIMIC_THIRD_RAMP_3 = ITEMS.registerSimpleBlockItem(ModBlocks.MIMIC_THIRD_RAMP_3);
    public static final DeferredItem<BlockItem> ASPHALT_THIRD_RAMP_1 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_THIRD_RAMP_1);
    public static final DeferredItem<BlockItem> ASPHALT_THIRD_RAMP_2 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_THIRD_RAMP_2);
    public static final DeferredItem<BlockItem> ASPHALT_THIRD_RAMP_3 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_THIRD_RAMP_3);
    public static final DeferredItem<BlockItem> CONCRETE_THIRD_RAMP_1 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_THIRD_RAMP_1);
    public static final DeferredItem<BlockItem> CONCRETE_THIRD_RAMP_2 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_THIRD_RAMP_2);
    public static final DeferredItem<BlockItem> CONCRETE_THIRD_RAMP_3 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_THIRD_RAMP_3);
    public static final DeferredItem<BlockItem> ASPHALT_PLAIN_THIRD_RAMP_1 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_PLAIN_THIRD_RAMP_1);
    public static final DeferredItem<BlockItem> ASPHALT_PLAIN_THIRD_RAMP_2 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_PLAIN_THIRD_RAMP_2);
    public static final DeferredItem<BlockItem> ASPHALT_PLAIN_THIRD_RAMP_3 = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_PLAIN_THIRD_RAMP_3);
    public static final DeferredItem<BlockItem> CONCRETE_PLAIN_THIRD_RAMP_1 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_PLAIN_THIRD_RAMP_1);
    public static final DeferredItem<BlockItem> CONCRETE_PLAIN_THIRD_RAMP_2 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_PLAIN_THIRD_RAMP_2);
    public static final DeferredItem<BlockItem> CONCRETE_PLAIN_THIRD_RAMP_3 = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_PLAIN_THIRD_RAMP_3);
    public static final DeferredItem<BlockItem> ASPHALT_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_RAMP);
    public static final DeferredItem<BlockItem> UPPER_ASPHALT_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.UPPER_ASPHALT_RAMP);
    public static final DeferredItem<BlockItem> FULL_ASPHALT_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.FULL_ASPHALT_RAMP);
    public static final DeferredItem<BlockItem> CONCRETE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_RAMP);
    public static final DeferredItem<BlockItem> UPPER_CONCRETE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.UPPER_CONCRETE_RAMP);
    public static final DeferredItem<BlockItem> FULL_CONCRETE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.FULL_CONCRETE_RAMP);
    public static final DeferredItem<BlockItem> ASPHALT_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_LINE_RAMP);
    public static final DeferredItem<BlockItem> UPPER_ASPHALT_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.UPPER_ASPHALT_LINE_RAMP);
    public static final DeferredItem<BlockItem> FULL_ASPHALT_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.FULL_ASPHALT_LINE_RAMP);
    public static final DeferredItem<BlockItem> CONCRETE_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_LINE_RAMP);
    public static final DeferredItem<BlockItem> UPPER_CONCRETE_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.UPPER_CONCRETE_LINE_RAMP);
    public static final DeferredItem<BlockItem> FULL_CONCRETE_LINE_RAMP = ITEMS.registerSimpleBlockItem(ModBlocks.FULL_CONCRETE_LINE_RAMP);
    public static final DeferredItem<BlockItem> BLACK_WHITE_BLOCK =
            ITEMS.registerSimpleBlockItem(ModBlocks.BLACK_WHITE_BLOCK);
    public static final DeferredItem<BlockItem> ROADBLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.ROADBLOCK);
    public static final DeferredItem<BlockItem> ASPHALT_ROAD =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_ROAD);
    public static final DeferredItem<BlockItem> ASPHALT_ROAD_LINE =
            ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_ROAD_LINE);
    public static final DeferredItem<BlockItem> CONCRETE_ROAD =
            ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_ROAD);
    public static final DeferredItem<BlockItem> CONCRETE_ROAD_LINE =
            ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_ROAD_LINE);
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE = ITEMS.register(
            "assembly_table",
            () -> new StaticGeoBlockItem(
                    ModBlocks.ASSEMBLY_TABLE.get(),
                    new Item.Properties(),
                    "assembly_table"
            )
    );
    public static final DeferredItem<BlockItem> FUEL_EXTRACTOR = ITEMS.register(
            "fuel_extractor",
            () -> new StaticGeoBlockItem(
                    ModBlocks.FUEL_EXTRACTOR.get(),
                    new Item.Properties(),
                    "fuel_extractor"
            )
    );
    public static final DeferredItem<BlockItem> VEHICLE_MANUFACTURING_TABLE = ITEMS.register(
            "vehicle_manufacturing_table",
            () -> new StaticGeoBlockItem(
                    ModBlocks.VEHICLE_MANUFACTURING_TABLE.get(),
                    new Item.Properties(),
                    "vehicle_manufacturing_table"
            )
    );
    public static final DeferredItem<Item> ALLOY_INGOT = ITEMS.register(
            "alloy_ingot", () -> new StaticGeoItem(new Item.Properties(), "alloy_ingot_model"));
    public static final DeferredItem<Item> COMPRESSED_ALLOY_PLATE = ITEMS.register(
            "compressed_alloy_plate",
            () -> new StaticGeoItem(new Item.Properties(), "compressed_alloy_plate_model"));

    public static final DeferredItem<Item> SLIME_RUBBER = ITEMS.register(
            "slime_rubber", () -> new StaticGeoItem(new Item.Properties(), "slime_rubber_model"));

    private static DeferredItem<VehicleItem> vehicle(VehicleDefinition definition) {
        return ITEMS.register(definition.id().getPath() + "_vehicle", () -> new VehicleItem(
                new Item.Properties().stacksTo(1),
                definition
        ));
    }

    private static DeferredItem<VehicleTireItem> tire(VehicleTireDefinition definition) {
        return ITEMS.register(definition.id().getPath(), () -> new VehicleTireItem(
                new Item.Properties().stacksTo(1),
                definition.id(),
                definition.maxDurability()
        ));
    }

    private static DeferredItem<VehicleEngineItem> engine(VehicleEngineDefinition definition) {
        return ITEMS.register(definition.id().getPath(), () -> new VehicleEngineItem(
                new Item.Properties().stacksTo(1),
                definition
        ));
    }

    private static DeferredItem<VehiclePowerUnitItem> powerUnit(VehiclePowerDefinition definition) {
        return ITEMS.register(definition.id().getPath(), () -> new VehiclePowerUnitItem(
                new Item.Properties().stacksTo(1),
                definition
        ));
    }

    private static DeferredItem<VehiclePaintItem> paint(VehiclePaintDefinition definition) {
        return ITEMS.register(definition.id().getPath() + "_vehicle_paint", () -> new VehiclePaintItem(
                new Item.Properties().stacksTo(16),
                definition
        ));
    }

    public static final DeferredItem<VehicleTireItem> ROAD_TIRE = tire(VehicleTireDefinitions.ROAD);
    public static final DeferredItem<VehicleTireItem> ALL_TERRAIN_TIRE = tire(VehicleTireDefinitions.ALL_TERRAIN);
    public static final DeferredItem<VehicleTireItem> RUN_FLAT_TIRE = tire(VehicleTireDefinitions.RUN_FLAT);
    public static final DeferredItem<VehicleTireItem> RACING_SPEED_TIRE = tire(VehicleTireDefinitions.RACING_SPEED);
    public static final DeferredItem<VehicleTireItem> RACING_BALANCED_TIRE = tire(
            VehicleTireDefinitions.RACING_BALANCED
    );
    public static final DeferredItem<VehicleTireItem> RACING_ENDURANCE_TIRE = tire(
            VehicleTireDefinitions.RACING_ENDURANCE
    );
    public static final DeferredItem<VehicleEngineItem> DRIVE_MOTOR = engine(
            VehicleEngineDefinitions.DRIVE_MOTOR
    );
    public static final DeferredItem<VehicleEngineItem> SMALL_ENGINE = engine(VehicleEngineDefinitions.SMALL);
    public static final DeferredItem<VehicleEngineItem> MEDIUM_ENGINE = engine(VehicleEngineDefinitions.MEDIUM);
    public static final DeferredItem<VehicleEngineItem> LARGE_ENGINE = engine(VehicleEngineDefinitions.LARGE);
    public static final DeferredItem<VehicleEngineItem> RACING_ENGINE_V = engine(VehicleEngineDefinitions.RACING_V);
    public static final DeferredItem<VehicleEngineItem> RACING_ENGINE_A = engine(VehicleEngineDefinitions.RACING_A);
    public static final DeferredItem<VehicleEngineItem> SUPER_RACING_ENGINE_V = engine(
            VehicleEngineDefinitions.SUPER_RACING_V
    );
    public static final DeferredItem<VehicleEngineItem> SUPER_RACING_ENGINE_A = engine(
            VehicleEngineDefinitions.SUPER_RACING_A
    );
    public static final DeferredItem<VehiclePowerUnitItem> EXTRA_POWER_UNIT_A =
            powerUnit(VehiclePowerDefinitions.EXTRA_A);
    public static final DeferredItem<VehiclePowerUnitItem> EXTRA_POWER_UNIT_B =
            powerUnit(VehiclePowerDefinitions.EXTRA_B);
    public static final DeferredItem<VehiclePowerUnitItem> ENERGY_RECOVERY_UNIT_A =
            powerUnit(VehiclePowerDefinitions.RECOVERY_A);
    public static final DeferredItem<VehiclePowerUnitItem> ENERGY_RECOVERY_UNIT_B =
            powerUnit(VehiclePowerDefinitions.RECOVERY_B);
    public static final DeferredItem<VehiclePaintItem> WHITE_VEHICLE_PAINT = paint(VehiclePaintDefinitions.WHITE);
    public static final DeferredItem<VehiclePaintItem> BLACK_VEHICLE_PAINT = paint(VehiclePaintDefinitions.BLACK);
    public static final DeferredItem<VehiclePaintItem> BLUE_VEHICLE_PAINT = paint(VehiclePaintDefinitions.BLUE);
    public static final DeferredItem<VehiclePaintItem> PINK_VEHICLE_PAINT = paint(VehiclePaintDefinitions.PINK);
    public static final DeferredItem<VehiclePaintItem> GREEN_VEHICLE_PAINT = paint(VehiclePaintDefinitions.GREEN);
    public static final DeferredItem<VehiclePaintItem> RED_VEHICLE_PAINT = paint(VehiclePaintDefinitions.RED);
    public static final DeferredItem<VehiclePaintItem> BREAD_VEHICLE_PAINT = paint(VehiclePaintDefinitions.BREAD);
    public static final DeferredItem<VehicleItem> KART_VEHICLE = vehicle(VehicleDefinitions.KART);
    public static final DeferredItem<VehicleItem> ELDERLY_CAR_VEHICLE = vehicle(VehicleDefinitions.ELDERLY_CAR);
    public static final DeferredItem<VehicleItem> SMALL_BUS_VEHICLE = vehicle(VehicleDefinitions.SMALL_BUS);
    public static final DeferredItem<VehicleItem> SMALL_CAR_VEHICLE = vehicle(VehicleDefinitions.SMALL_CAR);
    public static final DeferredItem<VehicleItem> TWO_SEAT_CAR_VEHICLE = vehicle(VehicleDefinitions.TWO_SEAT_CAR);
    public static final DeferredItem<VehicleItem> SUV_VEHICLE = vehicle(VehicleDefinitions.SUV);
    public static final DeferredItem<VehicleItem> CONVERTIBLE_VEHICLE = vehicle(VehicleDefinitions.CONVERTIBLE);
    public static final DeferredItem<VehicleItem> FORMULA_CAR_VEHICLE = vehicle(VehicleDefinitions.FORMULA_CAR);
    public static final DeferredItem<VehicleItem> RALLY_CAR_VEHICLE = ITEMS.register("rally_car_vehicle",
            () -> new ModeledVehicleItem(
                    new Item.Properties().stacksTo(1), VehicleDefinitions.RALLY_CAR));
    public static final DeferredItem<VehicleFuelTankItem> SMALL_FUEL_TANK = ITEMS.register(
            "small_fuel_tank",
            () -> new VehicleFuelTankItem(
                    new Item.Properties().stacksTo(1),
                    3_600,
                    200,
                    "oil_drum_model",
                    "oil_drum_model"
            )
    );
    public static final DeferredItem<VehicleFuelTankItem> LARGE_FUEL_TANK = ITEMS.register(
            "large_fuel_tank",
            () -> new VehicleFuelTankItem(
                    new Item.Properties().stacksTo(1),
                    7_200,
                    400,
                    "oil_drum2",
                    "oil_drum2_model"
            )
    );
    public static final DeferredItem<VehicleWrenchItem> WRENCH = ITEMS.register(
            "wrench", () -> new VehicleWrenchItem(new Item.Properties().stacksTo(1).durability(256), false));
    public static final DeferredItem<VehicleWrenchItem> ELECTRIC_WRENCH = ITEMS.register(
            "electric_wrench", () -> new VehicleWrenchItem(new Item.Properties().stacksTo(1).durability(1024), true));
    public static final DeferredItem<VehicleHammerItem> HAMMER = ITEMS.register(
            "hammer", () -> new VehicleHammerItem(new Item.Properties().stacksTo(1).durability(384)));
    public static final DeferredItem<VehicleJackItem> PORTABLE_JACK = ITEMS.register(
            "portable_jack", () -> new VehicleJackItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<VehicleKeyItem> VEHICLE_KEY = ITEMS.register(
            "vehicle_key", () -> new VehicleKeyItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<CrewDollItem> CREW_DOLL_FLECHAZO = ITEMS.register(
            "crew_doll_flechazo",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.FLECHAZO)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_NAIMIURA = ITEMS.register(
            "crew_doll_naimiura",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.NAIMIURA)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_BLUE_JUNZI = ITEMS.register(
            "crew_doll_blue_junzi",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.BLUE_JUNZI)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_ZHIFAN = ITEMS.register(
            "crew_doll_zhifan",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.ZHIFAN)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_DUCKDUCK = ITEMS.register(
            "crew_doll_duckduck",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.DUCKDUCK)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_NEPTUNE_TADASHI = ITEMS.register(
            "crew_doll_neptune_tadashi",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.NEPTUNE_TADASHI)
    );
    public static final DeferredItem<CrewDollItem> CREW_DOLL_YELLOW_DUCK = ITEMS.register(
            "crew_doll_yellow_duck",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.YELLOW_DUCK)
    );

    public static final DeferredItem<CrewDollItem> CREW_DOLL_SDJGE = ITEMS.register(
            "crew_doll_sdjge",
            () -> new CrewDollItem(new Item.Properties().stacksTo(1), CrewDollVariant.SDJGE)
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
