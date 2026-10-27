package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VehicleTireModel<T extends AbstractVehicleEntity> extends IsolatedGeoModel<T> {
    private final VehicleTireAppearance appearance;

    public VehicleTireModel() {
        this.appearance = null;
    }

    public VehicleTireModel(VehicleTireAppearance appearance) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
    }

    @Override
    protected BakedGeoModel loadSharedModel(ResourceLocation resource) {
        return appearance == null ? super.loadSharedModel(resource) : VehicleTireMeshes.get(resource);
    }

    @Override
    public ResourceLocation getModelResource(T vehicle) {
        if (appearance != null) {
            return appearance.model();
        }
        return PolarisVehicle.id("geo/vehicle/"
                + VehicleAssets.tireModelName(vehicle.vehicleTypeId())
                + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T vehicle) {
        if (appearance != null) {
            return appearance.texture();
        }
        return PolarisVehicle.id("textures/entity/vehicle/"
                + VehicleAssets.textureName(
                vehicle.vehicleTypeId(),
                vehicle.vehicleData().paintId()
        )
                + ".png");
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
        float wheelRotation = vehicle.wheelRenderRotation(animationState.getPartialTick());
        Map<String, Set<VehiclePartSlot>> boneSlots = appearance == null
                ? VehicleAssets.wheelBoneSlots(vehicle.vehicleTypeId())
                : appearance.boneSlots();
        for (Map.Entry<String, Set<VehiclePartSlot>> entry : boneSlots.entrySet()) {
            boolean installed = entry.getValue().stream()
                    .anyMatch(slot -> rendersSlot(vehicle, slot));
            getBone(entry.getKey()).ifPresent(bone -> {
                bone.setHidden(!installed);
                if (installed) {
                    int tightening = entry.getValue().stream()
                            .filter(slot -> rendersSlot(vehicle, slot))
                            .mapToInt(vehicle::wheelTightening)
                            .min()
                            .orElse(4);
                    VehicleTireAppearance.BonePose pose = authoredPose(entry.getKey(), bone);
                    bone.setScaleX(pose.scaleX());
                    bone.setScaleY(pose.scaleY());
                    bone.setScaleZ(pose.scaleZ());
                    float suspensionOffset = (float) entry.getValue().stream()
                            .filter(slot -> rendersSlot(vehicle, slot))
                            .mapToDouble(slot -> vehicle.wheelSuspensionRenderOffset(
                                    slot,
                                    animationState.getPartialTick()
                            ))
                            .average()
                            .orElse(0.0);
                    bone.setPosY(pose.y() + suspensionOffset);
                    applyTighteningPosition(bone, pose, tightening, appearance != null);
                    bone.setRotX(bone.getInitialSnapshot().getRotX() + wheelRotation);
                }
            });
        }
        float steeringAngle = vehicle.steeringRenderAngle(animationState.getPartialTick());
        Map<String, Float> steeringBones = appearance == null
                ? VehicleAssets.steeringBones(vehicle.vehicleTypeId())
                : appearance.steeringBones();
        for (Map.Entry<String, Float> entry : steeringBones.entrySet()) {
            var slots = boneSlots.get(entry.getKey());
            if (slots == null || slots.isEmpty()) continue;
            VehiclePartSlot slot = slots.iterator().next();
            getBone(entry.getKey()).ifPresent(bone -> bone.setRotY(
                    bone.getInitialSnapshot().getRotY()
                            + VehicleTireSteeringLimits.INSTANCE.roadWheelAngle(
                            vehicle.vehicleTypeId(), slot, steeringAngle, entry.getValue())
            ));
        }
    }

    static void applyTighteningPosition(GeoBone bone, VehicleTireAppearance.BonePose pose,
                                        int tightening, boolean fittedMesh) {
        float inwardOffset = 2.0F * tightening / 4.0F - (fittedMesh ? 2.0F : 0.0F);
        if (Math.abs(bone.getPivotX()) > 0.25F) {
            bone.setPosX(pose.x() + inwardOffset * Math.signum(bone.getPivotX()));
            bone.setPosZ(pose.z());
        } else {
            bone.setPosX(pose.x());
            bone.setPosZ(pose.z() - inwardOffset * Math.signum(bone.getPivotZ()));
        }
    }

    public boolean hasRenderableWheel(T vehicle) {
        if (appearance != null && !appearance.vehicleType().equals(vehicle.vehicleTypeId())) {
            return false;
        }
        Map<String, Set<VehiclePartSlot>> slots = appearance == null
                ? VehicleAssets.wheelBoneSlots(vehicle.vehicleTypeId())
                : appearance.boneSlots();
        return slots.values().stream().flatMap(Set::stream)
                .anyMatch(slot -> rendersSlot(vehicle, slot));
    }

    private boolean rendersSlot(T vehicle, VehiclePartSlot slot) {
        if (!vehicle.isWheelInstalled(slot)) {
            return false;
        }
        return vehicle.vehicleData().part(slot).fold(
                () -> false,
                part -> appearance == null
                        ? VehicleTireAppearances.find(
                        vehicle.vehicleTypeId(), part.partId()).isEmpty()
                        : appearance.vehicleType().equals(vehicle.vehicleTypeId())
                          && appearance.tireType().equals(part.partId())
        );
    }

    private VehicleTireAppearance.BonePose authoredPose(String boneName, GeoBone bone) {
        if (appearance != null) {
            VehicleTireAppearance.BonePose pose = appearance.bonePoses().get(boneName);
            if (pose != null) {
                return pose;
            }
        }
        var initial = bone.getInitialSnapshot();
        return new VehicleTireAppearance.BonePose(
                initial.getOffsetX(),
                initial.getOffsetY(),
                initial.getOffsetZ(),
                initial.getScaleX(),
                initial.getScaleY(),
                initial.getScaleZ()
        );
    }
}
