package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public final class ModeledVehicleItem extends VehicleItem implements GeoItem, StaticGeoAsset {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ModeledVehicleItem(Properties properties, VehicleDefinition definition) {
        super(properties, definition);
    }

    @Override
    public String geckoAssetFolder() {
        return "vehicle";
    }

    @Override
    public String geckoAssetName() {
        return VehicleAssets.modelName(definition().id()) + "_item";
    }

    @Override
    public String geckoTextureFolder() {
        return "entity/vehicle";
    }

    @Override
    public String geckoTextureName() {
        return VehicleAssets.textureName(definition().id());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<ModeledVehicleItem>install(consumer);
    }
}
