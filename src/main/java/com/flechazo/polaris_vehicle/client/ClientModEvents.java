package com.flechazo.polaris_vehicle.client;

import cc.sighs.oelib.config.ui.screen.ConfigScreen;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.util.OptionalOps;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.client.compat.render.ClientRenderCompatRegistry;
import com.flechazo.polaris_vehicle.client.hud.VehicleHudOverlay;
import com.flechazo.polaris_vehicle.client.model.*;
import com.flechazo.polaris_vehicle.client.particle.VehicleSlipstreamParticle;
import com.flechazo.polaris_vehicle.client.renderer.CrewDollEntityRenderer;
import com.flechazo.polaris_vehicle.client.renderer.VehicleRenderer;
import com.flechazo.polaris_vehicle.client.screen.AssemblyTableScreen;
import com.flechazo.polaris_vehicle.client.screen.FuelExtractorScreen;
import com.flechazo.polaris_vehicle.client.screen.VehicleManufacturingScreen;
import com.flechazo.polaris_vehicle.client.sound.VehicleEnginePcmBank;
import com.flechazo.polaris_vehicle.client.sound.VehicleHornPcmBank;
import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.registry.ModEntityTypes;
import com.flechazo.polaris_vehicle.registry.ModMenus;
import com.flechazo.polaris_vehicle.registry.ModParticles;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;
import java.util.stream.Stream;
// import net.neoforged.bus.api.EventPriority;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    private static final List<String> HELD_3D_ITEM_MODELS = List.of(
            "assembly_table", "fuel_extractor", "vehicle_manufacturing_table",
            "black_vehicle_paint", "blue_vehicle_paint", "pink_vehicle_paint",
            "green_vehicle_paint", "red_vehicle_paint", "white_vehicle_paint", "bread_vehicle_paint",
            "electric_wrench", "wrench", "hammer", "vehicle_key"
    );

    private static final List<String> FLAT_ITEM_MODELS = List.of(
            "portable_jack",
            "all_terrain_tire", "racing_balanced_tire", "racing_endurance_tire", "racing_speed_tire",
            "road_tire", "run_flat_tire",
            "drive_motor", "small_engine", "medium_engine", "large_engine",
            "racing_engine_type_a", "racing_engine_type_v", "super_racing_engine_type_a", "super_racing_engine_type_v",
            "energy_recovery_unit_a", "energy_recovery_unit_b", "extra_power_unit_a", "extra_power_unit_b",
            "small_fuel_tank", "large_fuel_tank", "rally_car_vehicle",
            "alloy_ingot", "compressed_alloy_plate", "slime_rubber"
    );

    private static final List<String> SIDECAR_SPRITE_ITEMS =
            Stream.concat(HELD_3D_ITEM_MODELS.stream(), FLAT_ITEM_MODELS.stream()).toList();

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientRenderCompatRegistry.initialize();
            Pathway.maybe(OptionalOps.toMaybe(ModList.get().getModContainerById(PolarisVehicle.MODID)))
                    .peek(container -> container.registerExtensionPoint(
                            IConfigScreenFactory.class,
                            (ignored, parent) -> new ConfigScreen(parent, PolarisVehicle.MODID)
                    ));
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.KART.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.ELDERLY_CAR.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMALL_BUS.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMALL_CAR.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.TWO_SEAT_CAR.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SUV.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CONVERTIBLE.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.FORMULA_CAR.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.RALLY_CAR.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CREW_DOLL.get(), CrewDollEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerTwoDimensionalItemModels(ModelEvent.RegisterAdditional event) {
        for (String item : SIDECAR_SPRITE_ITEMS) {
            event.register(ModelResourceLocation.standalone(
                    PolarisVehicle.id("item/" + item + "_2d"
                    )
            ));
        }
    }

    @SubscribeEvent
    public static void wrapDroppedItemModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        var missingModel = models.get(ModelBakery.MISSING_MODEL_VARIANT);
        for (String item : SIDECAR_SPRITE_ITEMS) {
            ResourceLocation itemId = PolarisVehicle.id(item);
            ModelResourceLocation inventoryId = ModelResourceLocation.inventory(itemId);
            var inventoryModel = models.get(inventoryId);
            var spriteModel = models.get(ModelResourceLocation.standalone(
                    itemId.withPath("item/" + item + "_2d")
            ));
            if (inventoryModel != null && inventoryModel.isCustomRenderer()
                    && spriteModel != null && spriteModel != missingModel) {
                models.put(inventoryId, FLAT_ITEM_MODELS.contains(item)
                        ? new PreviewOnlyGeoItemModel(inventoryModel, spriteModel)
                        : new GroundSpriteItemModel(inventoryModel, spriteModel));
            }
        }
    }

    // @SubscribeEvent(priority = EventPriority.LOWEST)
    @SubscribeEvent
    public static void wrapRampModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        models.replaceAll((location, model) -> {
            if (ModBlocks.isFixedRampId(location.id())) {
                return new FixedRampModel(model);
            }
            if (ModBlocks.isMimicRampId(location.id())) {
                var ramp = (RampBlock)
                        BuiltInRegistries.BLOCK.get(location.id());
                return new TerrainWrappedRampModel(model, ramp.lowHeight(), ramp.rise(), models::get);
            }
            return model;
        });
    }

    @SubscribeEvent
    public static void registerRampColors(RegisterColorHandlersEvent.Block event) {
        var colors = event.getBlockColors();
        event.register((state, level, position, tintIndex) -> {
                    if (level == null || position == null) {
                        return -1;
                    }
                    var cursor = position.mutable().move(Direction.DOWN);
                    for (int depth = 0; depth < 64 && cursor.getY() >= level.getMinBuildHeight();
                         depth++, cursor.move(Direction.DOWN)) {
                        var source = level.getBlockState(cursor);
                        if (ModBlocks.isMimicRampId(BuiltInRegistries.BLOCK.getKey(source.getBlock()))) {
                            continue;
                        }
                        return source.isAir()
                                ? -1
                                : colors.getColor(source, level, cursor, tintIndex);
                    }
                    return -1;
                }, ModBlocks.MIMIC_RAMP.get(), ModBlocks.UPPER_MIMIC_RAMP.get(),
                ModBlocks.FULL_MIMIC_RAMP.get(), ModBlocks.MIMIC_THIRD_RAMP_1.get(),
                ModBlocks.MIMIC_THIRD_RAMP_2.get(), ModBlocks.MIMIC_THIRD_RAMP_3.get());
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SLIPSTREAM.get(),
                VehicleSlipstreamParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VehicleKeyMappings.ENGINE_TOGGLE);
        event.register(VehicleKeyMappings.BRAKE);
        event.register(VehicleKeyMappings.BOOST);
        event.register(VehicleKeyMappings.HORN);
        event.register(VehicleKeyMappings.CAMERA_TOGGLE);
        event.register(VehicleKeyMappings.COLLISION_DEBUG);
        event.register(VehicleKeyMappings.VEHICLE_PARAMETERS);
        event.register(VehicleKeyMappings.VEHICLE_TELEMETRY);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(VehicleEnginePcmBank.INSTANCE);
        event.registerReloadListener(VehicleHornPcmBank.INSTANCE);
        event.registerReloadListener(VehicleTireSteeringLimits.INSTANCE);
        event.registerReloadListener(VehicleHumanoidAnimations.INSTANCE);
        event.registerReloadListener(VehicleStructureModelManager.CLIENT);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLY_TABLE.get(), AssemblyTableScreen::new);
        event.register(ModMenus.FUEL_EXTRACTOR.get(), FuelExtractorScreen::new);
        event.register(ModMenus.VEHICLE_MANUFACTURING_TABLE.get(), VehicleManufacturingScreen::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                PolarisVehicle.id("vehicle_hud"),
                VehicleHudOverlay::render
        );
    }
}
