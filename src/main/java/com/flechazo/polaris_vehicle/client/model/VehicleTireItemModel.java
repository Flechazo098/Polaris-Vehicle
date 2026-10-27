package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.VehicleTireItem;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public final class VehicleTireItemModel extends IsolatedGeoModel<VehicleTireItem> {
    @Override
    protected BakedGeoModel loadSharedModel(ResourceLocation resource) {
        return VehicleTireMeshes.get(resource);
    }

    @Override
    public ResourceLocation getModelResource(VehicleTireItem tire) {
        return VehicleTireAppearances.find(VehicleDefinitions.FORMULA_CAR_ID, tire.partId())
                .fold(() -> {
                    throw new IllegalStateException("Missing formula-car item appearance for " + tire.partId());
                }, appearance -> appearance)
                .model();
    }

    @Override
    public ResourceLocation getTextureResource(VehicleTireItem tire) {
        return VehicleTireAppearances.find(VehicleDefinitions.FORMULA_CAR_ID, tire.partId())
                .fold(() -> {
                    throw new IllegalStateException("Missing formula-car item appearance for " + tire.partId());
                }, appearance -> appearance)
                .texture();
    }

    @Override
    public ResourceLocation getAnimationResource(VehicleTireItem tire) {
        return PolarisVehicle.id("animations/static.animation.json"
        );
    }

    @Override
    public void setCustomAnimations(
            VehicleTireItem tire,
            long instanceId,
            AnimationState<VehicleTireItem> animationState
    ) {
        getBone("1").ifPresent(bone -> {
            bone.setHidden(false);
            bone.setPosX(bone.getPivotX());
            bone.setPosY(-bone.getPivotY());
            bone.setPosZ(-bone.getPivotZ());
        });
        getBone("2").ifPresent(bone -> bone.setHidden(true));
        getBone("3").ifPresent(bone -> bone.setHidden(true));
        getBone("4").ifPresent(bone -> bone.setHidden(true));
    }
}
