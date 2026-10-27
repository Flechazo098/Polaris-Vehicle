package com.flechazo.polaris_vehicle;

import cc.sighs.oelib.network.api.NetworkAutoRegistration;
import com.flechazo.polaris_vehicle.config.VehicleClientConfig;
import com.flechazo.polaris_vehicle.config.VehicleGameplayConfig;
import com.flechazo.polaris_vehicle.model.PolyMeshBakedModelFactory;
import com.flechazo.polaris_vehicle.part.VehicleRepairRecipeManager;
import com.flechazo.polaris_vehicle.registry.*;
import com.flechazo.polaris_vehicle.sparkcore.SparkCore;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTireParameters;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.entity.VehicleMaintenanceInteractions;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsManager;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import software.bernie.geckolib.util.GeckoLibUtil;

@Mod(PolarisVehicle.MODID)
public class PolarisVehicle {
    public static final String MODID = "polaris_vehicle";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public PolarisVehicle(IEventBus modEventBus, ModContainer modContainer) {
        GeckoLibUtil.addCustomBakedModelFactory(MODID, new PolyMeshBakedModelFactory());

        NetworkAutoRegistration.registerBasePackage("com.flechazo.polaris_vehicle.network");
        VehicleTypeParameters.register();
        VehicleTireParameters.register();
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        VehicleGameplayConfig.register();
        VehicleClientConfig.register();
        VehicleStructureModelManager.registerServerListener();
        VehicleRepairRecipeManager.registerServerListener();
        SparkCore.register(modEventBus);
        VehiclePhysicsManager.register();
        VehicleMaintenanceInteractions.register();
    }
}
