package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.fuel.PortableFuelTankData;
import com.flechazo.polaris_vehicle.item.VehicleFuelTankItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            PolarisVehicle.MODID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.polaris_vehicle.main"))
                    .icon(() -> new ItemStack(ModItems.KART_VEHICLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ALLOY_INGOT.get());
                        output.accept(ModItems.COMPRESSED_ALLOY_PLATE.get());
                        output.accept(ModItems.SLIME_RUBBER.get());
                        output.accept(ModItems.ROAD_TIRE.get());
                        output.accept(ModItems.ALL_TERRAIN_TIRE.get());
                        output.accept(ModItems.RUN_FLAT_TIRE.get());
                        output.accept(ModItems.RACING_SPEED_TIRE.get());
                        output.accept(ModItems.RACING_BALANCED_TIRE.get());
                        output.accept(ModItems.RACING_ENDURANCE_TIRE.get());
                        output.accept(ModItems.DRIVE_MOTOR.get());
                        output.accept(ModItems.SMALL_ENGINE.get());
                        output.accept(ModItems.MEDIUM_ENGINE.get());
                        output.accept(ModItems.LARGE_ENGINE.get());
                        output.accept(ModItems.RACING_ENGINE_V.get());
                        output.accept(ModItems.RACING_ENGINE_A.get());
                        output.accept(ModItems.SUPER_RACING_ENGINE_V.get());
                        output.accept(ModItems.SUPER_RACING_ENGINE_A.get());
                        output.accept(ModItems.EXTRA_POWER_UNIT_A.get());
                        output.accept(ModItems.EXTRA_POWER_UNIT_B.get());
                        output.accept(ModItems.ENERGY_RECOVERY_UNIT_A.get());
                        output.accept(ModItems.ENERGY_RECOVERY_UNIT_B.get());
                        output.accept(ModItems.WHITE_VEHICLE_PAINT.get());
                        output.accept(ModItems.BLACK_VEHICLE_PAINT.get());
                        output.accept(ModItems.BLUE_VEHICLE_PAINT.get());
                        output.accept(ModItems.PINK_VEHICLE_PAINT.get());
                        output.accept(ModItems.GREEN_VEHICLE_PAINT.get());
                        output.accept(ModItems.RED_VEHICLE_PAINT.get());
                        output.accept(ModItems.BREAD_VEHICLE_PAINT.get());
                        output.accept(ModItems.KART_VEHICLE.get());
                        output.accept(ModItems.ELDERLY_CAR_VEHICLE.get());
                        output.accept(ModItems.SMALL_BUS_VEHICLE.get());
                        output.accept(ModItems.SMALL_CAR_VEHICLE.get());
                        output.accept(ModItems.TWO_SEAT_CAR_VEHICLE.get());
                        output.accept(ModItems.SUV_VEHICLE.get());
                        output.accept(ModItems.CONVERTIBLE_VEHICLE.get());
                        output.accept(ModItems.FORMULA_CAR_VEHICLE.get());
                        output.accept(ModItems.RALLY_CAR_VEHICLE.get());
                        output.accept(ModItems.SMALL_FUEL_TANK.get());
                        output.accept(ModItems.LARGE_FUEL_TANK.get());
                        output.accept(fullFuelTank(ModItems.SMALL_FUEL_TANK.get()));
                        output.accept(fullFuelTank(ModItems.LARGE_FUEL_TANK.get()));
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModItems.ELECTRIC_WRENCH.get());
                        output.accept(ModItems.HAMMER.get());
                        output.accept(ModItems.PORTABLE_JACK.get());
                        output.accept(ModItems.VEHICLE_KEY.get());
                    })
                    .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = TABS.register(
            "blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.polaris_vehicle.blocks"))
                    .icon(() -> new ItemStack(ModItems.VEHICLE_MANUFACTURING_TABLE.get()))
                    .withTabsBefore(MAIN.getId())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MIMIC_RAMP.get());
                        output.accept(ModItems.UPPER_MIMIC_RAMP.get());
                        output.accept(ModItems.FULL_MIMIC_RAMP.get());
                        output.accept(ModItems.ASPHALT_RAMP.get());
                        output.accept(ModItems.UPPER_ASPHALT_RAMP.get());
                        output.accept(ModItems.FULL_ASPHALT_RAMP.get());
                        output.accept(ModItems.CONCRETE_RAMP.get());
                        output.accept(ModItems.UPPER_CONCRETE_RAMP.get());
                        output.accept(ModItems.FULL_CONCRETE_RAMP.get());
                        output.accept(ModItems.ASPHALT_LINE_RAMP.get());
                        output.accept(ModItems.UPPER_ASPHALT_LINE_RAMP.get());
                        output.accept(ModItems.FULL_ASPHALT_LINE_RAMP.get());
                        output.accept(ModItems.CONCRETE_LINE_RAMP.get());
                        output.accept(ModItems.UPPER_CONCRETE_LINE_RAMP.get());
                        output.accept(ModItems.FULL_CONCRETE_LINE_RAMP.get());
                        output.accept(ModItems.MIMIC_THIRD_RAMP_1.get());
                        output.accept(ModItems.MIMIC_THIRD_RAMP_2.get());
                        output.accept(ModItems.MIMIC_THIRD_RAMP_3.get());
                        output.accept(ModItems.ASPHALT_THIRD_RAMP_1.get());
                        output.accept(ModItems.ASPHALT_THIRD_RAMP_2.get());
                        output.accept(ModItems.ASPHALT_THIRD_RAMP_3.get());
                        output.accept(ModItems.ASPHALT_PLAIN_THIRD_RAMP_1.get());
                        output.accept(ModItems.ASPHALT_PLAIN_THIRD_RAMP_2.get());
                        output.accept(ModItems.ASPHALT_PLAIN_THIRD_RAMP_3.get());
                        output.accept(ModItems.CONCRETE_THIRD_RAMP_1.get());
                        output.accept(ModItems.CONCRETE_THIRD_RAMP_2.get());
                        output.accept(ModItems.CONCRETE_THIRD_RAMP_3.get());
                        output.accept(ModItems.CONCRETE_PLAIN_THIRD_RAMP_1.get());
                        output.accept(ModItems.CONCRETE_PLAIN_THIRD_RAMP_2.get());
                        output.accept(ModItems.CONCRETE_PLAIN_THIRD_RAMP_3.get());
                        output.accept(ModItems.BLACK_WHITE_BLOCK.get());
                        output.accept(ModItems.ROADBLOCK.get());
                        output.accept(ModItems.ASPHALT_ROAD.get());
                        output.accept(ModItems.ASPHALT_ROAD_LINE.get());
                        output.accept(ModItems.CONCRETE_ROAD.get());
                        output.accept(ModItems.CONCRETE_ROAD_LINE.get());
                        output.accept(ModItems.ASSEMBLY_TABLE.get());
                        output.accept(ModItems.FUEL_EXTRACTOR.get());
                        output.accept(ModItems.VEHICLE_MANUFACTURING_TABLE.get());
                        output.accept(ModItems.CREW_DOLL_FLECHAZO.get());
                        output.accept(ModItems.CREW_DOLL_NAIMIURA.get());
                        output.accept(ModItems.CREW_DOLL_BLUE_JUNZI.get());
                        output.accept(ModItems.CREW_DOLL_ZHIFAN.get());
                        output.accept(ModItems.CREW_DOLL_DUCKDUCK.get());
                        output.accept(ModItems.CREW_DOLL_NEPTUNE_TADASHI.get());
                        output.accept(ModItems.CREW_DOLL_SDJGE.get());
                        output.accept(ModItems.CREW_DOLL_YELLOW_DUCK.get());
                    })
                    .build()
    );

    private static ItemStack fullFuelTank(VehicleFuelTankItem item) {
        ItemStack stack = new ItemStack(item);
        item.writeData(stack, PortableFuelTankData.empty().withAmount(item.capacityMl(), item.capacityMl()));
        return stack;
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
