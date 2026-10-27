package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.AnimationVector;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.HumanoidAnimationPose;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.HumanoidBoneTransform;
import com.flechazo.polaris_vehicle.util.AnimationUtil;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    private static final float RIDER_LEG_SHELL_CLEARANCE = 0.375F;
    private static final float CARRY_CROUCH_LEAN = 0.5F;

    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart hat;

    @Shadow
    @Final
    public ModelPart body;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightLeg;

    @Shadow
    @Final
    public ModelPart leftLeg;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD")
    )
    private void resetAccumulatedVehiclePoseOffsets(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        resetPosition(head);
        resetPosition(hat);
        resetPosition(body);
        resetPosition(rightArm);
        resetPosition(leftArm);
        resetPosition(rightLeg);
        resetPosition(leftLeg);
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void applyVehicleDriverPose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (entity.getVehicle() instanceof AbstractVehicleEntity vehicle
                && vehicle.isDriver(entity)) {
            VehicleHumanoidAnimations.INSTANCE.poseFor(vehicle, ageInTicks - entity.tickCount)
                    .ifPresent(pose -> applyPose(pose, true));
            return;
        }
        if (entity.getVehicle() instanceof AbstractVehicleEntity vehicle) {
            VehicleHumanoidAnimations.INSTANCE.seatedPassengerPoseFor(vehicle)
                    .ifPresent(this::applyPassengerLegPlacement);
            return;
        }
        boolean carryingVehicle = !entity.level().getEntitiesOfClass(
                AbstractVehicleEntity.class,
                entity.getBoundingBox().inflate(4.0),
                candidate -> candidate.isCarriedBy(player)
        ).isEmpty();
        if (carryingVehicle) {
            applyPose(
                    VehicleHumanoidAnimations.INSTANCE.carryPose(ageInTicks),
                    false
            );
            if (entity.isCrouching()) {
                rightArm.xRot += CARRY_CROUCH_LEAN;
                leftArm.xRot += CARRY_CROUCH_LEAN;
            }
        }
    }

    private void applyPassengerLegPlacement(HumanoidAnimationPose pose) {
        AnimationVector bodyPosition = pose.bone("body")
                .flatMap(HumanoidBoneTransform::position)
                .orElse(AnimationVector.ZERO);
        applyPartPosition(rightLeg, pose, "rightLeg", bodyPosition);
        applyPartPosition(leftLeg, pose, "leftLeg", bodyPosition);
    }

    private void applyPartPosition(
            ModelPart part,
            HumanoidAnimationPose pose,
            String boneName,
            AnimationVector bodyPosition
    ) {
        pose.bone(boneName)
                .flatMap(HumanoidBoneTransform::position)
                .ifPresent(position -> {
                    part.x += position.x() - bodyPosition.x();
                    part.y += -position.y() + bodyPosition.y()
                            - RIDER_LEG_SHELL_CLEARANCE;
                    part.z += position.z() - bodyPosition.z();
                });
    }

    private void applyPose(
            HumanoidAnimationPose pose,
            boolean positionsRelativeToBody
    ) {
        AnimationVector bodyPosition = positionsRelativeToBody
                ? pose.bone("body")
                .flatMap(HumanoidBoneTransform::position)
                .orElse(AnimationVector.ZERO)
                : AnimationVector.ZERO;

        applyPart(body, pose, "body", bodyPosition, false, positionsRelativeToBody);
        applyPart(head, pose, "head", bodyPosition, true, positionsRelativeToBody);
        applyPart(rightArm, pose, "rightArm", bodyPosition, false, positionsRelativeToBody);
        applyPart(leftArm, pose, "leftArm", bodyPosition, false, positionsRelativeToBody);
        applyPart(rightLeg, pose, "rightLeg", bodyPosition, false, positionsRelativeToBody);
        applyPart(leftLeg, pose, "leftLeg", bodyPosition, false, positionsRelativeToBody);
        hat.copyFrom(head);
    }

    private void applyPart(
            ModelPart part,
            HumanoidAnimationPose pose,
            String boneName,
            AnimationVector bodyPosition,
            boolean additiveRotation,
            boolean bedrockModelFrame
    ) {
        pose.bone(boneName).ifPresent(transform -> {
            transform.position().ifPresent(position -> {
                float targetX = part.x + position.x() - bodyPosition.x();
                float targetY = part.y - position.y() + bodyPosition.y();
                if (bedrockModelFrame && ("rightArm".equals(boneName) || "leftArm".equals(boneName))) {
                    targetY = AnimationUtil.shoulderY(part.y, position.y() - bodyPosition.y());
                }
                if ("rightLeg".equals(boneName) || "leftLeg".equals(boneName)) {
                    targetY -= RIDER_LEG_SHELL_CLEARANCE;
                }
                float targetZ = part.z + position.z() - bodyPosition.z();
                part.x = targetX;
                part.y = targetY;
                part.z = targetZ;
            });
            transform.rotation().ifPresent(rotation -> {
                float xRotation = rotation.x() * Mth.DEG_TO_RAD;
                float yRotation = rotation.y() * Mth.DEG_TO_RAD;
                float zRotation = rotation.z() * Mth.DEG_TO_RAD;
                if (additiveRotation) {
                    part.xRot += xRotation;
                    part.yRot += yRotation;
                    part.zRot += zRotation;
                } else {
                    part.xRot = xRotation;
                    part.yRot = yRotation;
                    part.zRot = zRotation;
                }
            });
        });
    }

    private static void resetPosition(ModelPart part) {
        var initialPose = part.getInitialPose();
        part.x = initialPose.x;
        part.y = initialPose.y;
        part.z = initialPose.z;
    }
}
