package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.StaticGeoAsset;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoAnimatable;

public final class StaticGeoItemModel<T extends Item & GeoAnimatable & StaticGeoAsset> extends IsolatedGeoModel<T> {
    @Override
    public ResourceLocation getModelResource(T animatable) {
        return PolarisVehicle.id("geo/" + animatable.geckoAssetFolder() + "/"
                + animatable.geckoAssetName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return PolarisVehicle.id("textures/" + animatable.geckoTextureFolder() + "/"
                + animatable.geckoTextureName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return PolarisVehicle.id("animations/" + animatable.geckoAnimationName() + ".animation.json");
    }

}
