package com.flechazo.polaris_vehicle_more_liveries;

import com.flechazo.polaris_vehicle.item.VehiclePaintItem;
import com.flechazo.polaris_vehicle.registry.ModCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

@Mod(PolarisVehicleMoreLiveries.MODID)
public final class PolarisVehicleMoreLiveries {
    public static final String MODID = "polaris_vehicle_more_liveries";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<VehiclePaintItem> WHITE_SKIN = ITEMS.register("white_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<VehiclePaintItem> BLACK_SKIN = ITEMS.register("black_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<VehiclePaintItem> BLUE_SKIN = ITEMS.register("blue_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<VehiclePaintItem> PINK_SKIN = ITEMS.register("pink_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<VehiclePaintItem> GREEN_SKIN = ITEMS.register("green_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final DeferredItem<VehiclePaintItem> RED_SKIN = ITEMS.register("red_skin_vehicle_paint", () ->
            new VehiclePaintItem(new Item.Properties().stacksTo(64)));
    public static final List<DeferredItem<VehiclePaintItem>> PAINTS = List.of(WHITE_SKIN, BLACK_SKIN, BLUE_SKIN, PINK_SKIN, GREEN_SKIN, RED_SKIN);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKIN_PAINTS = TABS.register(
            "skin_paints", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".skin_paints"))
                    .withTabsBefore(ModCreativeModeTabs.MAIN.getKey())
                    .icon(() -> new ItemStack(WHITE_SKIN.get()))
                    .displayItems((parameters, output) -> PAINTS.forEach(paint -> output.accept(paint.get())))
                    .build());

    public PolarisVehicleMoreLiveries(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

}