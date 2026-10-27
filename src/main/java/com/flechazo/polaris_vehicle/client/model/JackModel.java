package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.VehicleJackItem;
import com.flechazo.polaris_vehicle.util.AnimationUtil;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

public final class JackModel extends IsolatedGeoModel<VehicleJackItem> {
    private double liftTick = Double.NaN;

    public void setLiftTick(double tick) {
        liftTick = tick;
    }

    @Override
    public ResourceLocation getModelResource(VehicleJackItem item) {
        return PolarisVehicle.id("geo/block/portable_jack.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(VehicleJackItem item) {
        return PolarisVehicle.id("textures/block/portable_jack.png");
    }

    @Override
    public ResourceLocation getAnimationResource(VehicleJackItem item) {
        return PolarisVehicle.id("animations/block/portable_jack.animation.json");
    }

    public void applyPose(VehicleJackItem item, BakedGeoModel model) {
        for (var root : model.topLevelBones()) resetPositions(root);
        var clip = getAnimation(item, Double.isNaN(liftTick) ? "animation.jack2.pose1" : "animation.jack2.tran");
        if (clip == null) return;
        double tick = Double.isNaN(liftTick) ? 0 : Math.clamp(liftTick, 0, clip.length());
        for (var channel : clip.boneAnimations()) {
            var bone = getAnimationProcessor().getBone(channel.boneName());
            if (bone == null) continue;
            var frames = channel.positionKeyFrames();
            bone.setPosX(AnimationUtil.sampleKeyframes(frames.xKeyframes(), tick));
            bone.setPosY(AnimationUtil.sampleKeyframes(frames.yKeyframes(), tick));
            bone.setPosZ(AnimationUtil.sampleKeyframes(frames.zKeyframes(), tick));
        }
    }

    private static void resetPositions(GeoBone bone) {
        var initial = bone.getInitialSnapshot();
        bone.updatePosition(initial.getOffsetX(), initial.getOffsetY(), initial.getOffsetZ());
        for (var child : bone.getChildBones()) resetPositions(child);
    }

    @Override
    public void setCustomAnimations(VehicleJackItem item, long instanceId, AnimationState<VehicleJackItem> state) {
        applyPose(item, getBakedModel(getModelResource(item)));
    }
}
