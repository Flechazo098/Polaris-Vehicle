package com.flechazo.polaris_vehicle_more_liveries;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.item.VehiclePaintItem;
import com.flechazo.polaris_vehicle.item.VehicleTooltips;
import com.flechazo.polaris_vehicle.registry.ModCreativeModeTabs;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintSkins;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod(PolarisVehicleMoreLiveries.MODID)
public final class PolarisVehicleMoreLiveries {
    public static final String MODID = "polaris_vehicle_more_liveries";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final Map<String, DeferredItem<VehiclePaintItem>> PAINTS = new LinkedHashMap<>();
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SKIN_PAINTS = TABS.register(
            "skin_paints", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID + ".skin_paints"))
                    .withTabsBefore(ModCreativeModeTabs.MAIN.getKey())
                    .icon(() -> new ItemStack(PAINTS.get("white_skin").get()))
                    .displayItems((parameters, output) -> PAINTS.values().forEach(paint -> output.accept(paint.get())))
                    .build());

    public PolarisVehicleMoreLiveries(IEventBus bus) {
        JsonObject manifest;
        try (var stream = PolarisVehicleMoreLiveries.class.getResourceAsStream("/assets/" + MODID + "/skins.json")) {
            if (stream == null) throw new IllegalStateException("Missing vehicle skin manifest");
            manifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read vehicle skin manifest", error);
        }
        Pathway.listPath(VehiclePaintDefinitions.ORDER)
                .filter(base -> VehiclePaintDefinitions.number(base).isDefined())
                .peek(base -> {
                    String colour = base.id().getPath();
                    var vehicles = new LinkedHashMap<ResourceLocation, VehiclePaintSkins.Assets>();
                    var details = new ArrayList<Component>();
                    details.add(VehicleTooltips.heading(Component.translatable("tooltip." + MODID + ".skin_paint.vehicles")));
                    for (var entry : manifest.entrySet()) {
                        var livery = entry.getValue().getAsJsonObject();
                        var vehicle = ResourceLocation.parse(livery.get("vehicle").getAsString());
                        var assets = new VehiclePaintSkins.Assets(
                                ResourceLocation.parse(livery.getAsJsonObject("textures").get(colour).getAsString()),
                                ResourceLocation.parse(livery.get("chassis").getAsString()),
                                ResourceLocation.parse(livery.get("control").getAsString()));
                        if (vehicles.putIfAbsent(vehicle, assets) != null) {
                            throw new IllegalStateException("Two liveries for " + vehicle + " in the skin manifest");
                        }
                        details.add(VehicleTooltips.item(
                                Component.translatable("tooltip." + MODID + "." + entry.getKey()), ChatFormatting.GRAY));
                    }
                    String name = colour + "_skin";
                    var paint = new VehiclePaintDefinition(id(name), "");
                    VehiclePaintSkins.register(paint, vehicles);
                    String item = name + "_vehicle_paint";
                    PAINTS.put(name, ITEMS.register(item, () -> new VehiclePaintItem(
                            new Item.Properties().stacksTo(64), paint,
                            "tooltip." + MODID + ".skin_paint", MODID + "/" + item + "_model", details)));
                });
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
