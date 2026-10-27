package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.menu.AssemblyTableMenu;
import com.flechazo.polaris_vehicle.content.menu.FuelExtractorMenu;
import com.flechazo.polaris_vehicle.content.menu.VehicleManufacturingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PolarisVehicle.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE =
            MENUS.register("assembly_table", () -> IMenuTypeExtension.create(
                    AssemblyTableMenu::new
            ));
    public static final DeferredHolder<MenuType<?>, MenuType<FuelExtractorMenu>> FUEL_EXTRACTOR =
            MENUS.register("fuel_extractor", () -> IMenuTypeExtension.create(
                    FuelExtractorMenu::new
            ));
    public static final DeferredHolder<MenuType<?>, MenuType<VehicleManufacturingMenu>> VEHICLE_MANUFACTURING_TABLE =
            MENUS.register("vehicle_manufacturing_table", () -> IMenuTypeExtension.create(
                    VehicleManufacturingMenu::new
            ));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
