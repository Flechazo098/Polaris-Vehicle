package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;

/**
 * Steering-wheel/control geometry split from the chassis and tire components.
 */
public final class VehicleControlModel<T extends AbstractVehicleEntity> extends GeoModel<T> {
    @Override
    public ResourceLocation getModelResource(T vehicle) {
        return VehicleAssets.paintControl(vehicle.vehicleTypeId(), vehicle.vehicleData().paintId());
    }

    @Override
    public ResourceLocation getTextureResource(T vehicle) {
        return VehicleAssets.paintTexture(vehicle.vehicleTypeId(), vehicle.vehicleData().paintId());
    }

    @Override
    public ResourceLocation getAnimationResource(T vehicle) {
        return PolarisVehicle.id("animations/vehicle/"
                + VehicleAssets.modelName(vehicle.vehicleTypeId())
                + ".animation.json");
    }

    @Override
    public void setCustomAnimations(
            T vehicle,
            long instanceId,
            AnimationState<T> animationState
    ) {
        var animations = VehicleHumanoidAnimations.INSTANCE;
        var pose = animations.controlPoseFor(vehicle.vehicleTypeId(),
                vehicle.steeringRenderProgress(animationState.getPartialTick()));
        for (String name : animations.controlBonesFor(vehicle.vehicleTypeId())) {
            getBone(name).ifPresent(bone -> {
                BoneSnapshot initial = bone.getInitialSnapshot();
                // Sample the supplied turn channels with the shared eased progress.
                // Reset the bind pose before applying them, including return to centre.
                bone.setRotX(initial.getRotX());
                bone.setRotY(initial.getRotY());
                bone.setRotZ(initial.getRotZ());
                bone.setPosX(initial.getOffsetX());
                bone.setPosY(initial.getOffsetY());
                bone.setPosZ(initial.getOffsetZ());
                pose.bone(name).ifPresent(channel -> {
                    channel.rotation().ifPresent(rotation -> {
                        bone.setRotX(initial.getRotX() - (float) Math.toRadians(rotation.x()));
                        bone.setRotY(initial.getRotY() - (float) Math.toRadians(rotation.y()));
                        bone.setRotZ(initial.getRotZ() + (float) Math.toRadians(rotation.z()));
                    });
                    channel.position().ifPresent(position -> {
                        bone.setPosX(position.x());
                        bone.setPosY(position.y());
                        bone.setPosZ(position.z());
                    });
                });
            });
        }
    }
}
