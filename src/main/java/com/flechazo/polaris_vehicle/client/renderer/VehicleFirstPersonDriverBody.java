package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.client.VehicleFirstPersonView;
import com.flechazo.polaris_vehicle.client.VehicleViewTransform;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;

import java.util.List;

public final class VehicleFirstPersonDriverBody {
    private static final List<String> VISIBLE_BONES = List.of(
            "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "head", "hat");

    private VehicleFirstPersonDriverBody() {
    }

    public static boolean usesDriverDummy(AbstractVehicleEntity vehicle) {
        return VehicleHumanoidAnimations.INSTANCE.hasDriverDummy(vehicle.vehicleTypeId());
    }

    public static VehicleFirstPersonView.Look limitLook(AbstractVehicleEntity vehicle, float partialTick,
                                                        VehicleFirstPersonView.Look previous, VehicleFirstPersonView.Look requested, float roll) {
        var skeleton = VehicleHumanoidAnimations.INSTANCE;
        var body = vehicle.vehicleBodyRenderRotation(partialTick);
        float steering = vehicle.steeringRenderProgress(partialTick);
        var boxes = skeleton.driverBodyBoxes(vehicle.vehicleTypeId(), steering);
        var nearPlane = Minecraft.getInstance().gameRenderer.getMainCamera().getNearPlane();
        double halfWidth = nearPlane.getTopLeft().distanceTo(nearPlane.getTopRight()) * 0.5;
        double halfHeight = nearPlane.getTopLeft().distanceTo(nearPlane.getBottomLeft()) * 0.5;
        return VehicleFirstPersonView.limitLook(previous, requested, look -> {
            var camera = VehicleViewTransform.cameraRotation(
                    body, look.yaw(), look.pitch(), roll);
            var headLook = VehicleFirstPersonView.lookingHeadTransform(new Matrix4f(), body, camera);
            return skeleton.driverEyeOffset(vehicle.vehicleTypeId(), steering, body, camera)
                    .map(eye -> VehicleFirstPersonView.intersectsCamera(
                            VehicleFirstPersonView.cameraEyeLocal(vehicle.vehicleTypeId(), eye),
                            headLook, boxes, halfWidth, halfHeight))
                    .orElse(false);
        });
    }

    public static Maybe<Vec3> eyeRenderPosition(AbstractVehicleEntity vehicle, float partialTick,
                                                Quaternionfc cameraRotation) {
        if (!usesDriverDummy(vehicle)) return Maybe.none();
        Vec3 previousOrigin = new Vec3(vehicle.xOld, vehicle.yOld, vehicle.zOld);
        var bodyRotation = vehicle.vehicleBodyRenderRotation(partialTick);
        return VehicleHumanoidAnimations.INSTANCE.driverEyeOffset(vehicle.vehicleTypeId(), vehicle.steeringRenderProgress(partialTick),
                        bodyRotation, cameraRotation)
                .map(localEye -> VehicleFirstPersonView.eyeInWorld(
                        VehicleFirstPersonView.cameraEyeLocal(vehicle.vehicleTypeId(), localEye), previousOrigin,
                        vehicle.position(), partialTick, bodyRotation));
    }

    public static Vec3 passengerEyeRenderPosition(AbstractVehicleEntity vehicle,
                                                  Player passenger, float partialTick, Quaternionfc cameraRotation) {
        var rotation = vehicle.vehicleBodyRenderRotation(partialTick);
        var driverEye = VehicleHumanoidAnimations.INSTANCE.driverEyeOffset(vehicle.vehicleTypeId(),
                        vehicle.steeringRenderProgress(partialTick), rotation, cameraRotation)
                .orElse(vehicle.driverSeatEyeLocalOffset(passenger));
        var eye = VehicleFirstPersonView.passengerEyeAtDriverHeight(vehicle.riderEyeLocalOffset(passenger), driverEye);
        var previousOrigin = new Vec3(vehicle.xOld, vehicle.yOld, vehicle.zOld);
        return VehicleFirstPersonView.eyeInWorld(VehicleFirstPersonView.cameraEyeLocal(vehicle.vehicleTypeId(), eye),
                previousOrigin, vehicle.position(), partialTick, rotation);
    }

    public static void render(AbstractVehicleEntity vehicle, PoseStack stack,
                              MultiBufferSource buffers, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (!usesDriverDummy(vehicle) || vehicle.isClientGuiPreview()
                || player == null || player.getVehicle() != vehicle || !vehicle.isDriver(player)
                || !minecraft.options.getCameraType().isFirstPerson()
                || minecraft.getCameraEntity() != player || player.isInvisible() || player.isSpectator()) {
            return;
        }
        var skeleton = VehicleHumanoidAnimations.INSTANCE;
        var camera = minecraft.gameRenderer.getMainCamera();
        var bodyRotation = vehicle.vehicleBodyRenderRotation(camera.getPartialTickTime());
        float steering = vehicle.steeringRenderProgress(camera.getPartialTickTime());
        var consumer = buffers.getBuffer(RenderType.entityCutout(player.getSkin().texture()));
        stack.pushPose();
        try {
            stack.mulPose(Axis.YP.rotationDegrees(180));
            for (String bone : VISIBLE_BONES) {
                skeleton.driverBoneTransform(vehicle.vehicleTypeId(), bone, steering).ifPresent(transform ->
                        skeleton.driverGeometry(vehicle.vehicleTypeId(), bone).ifPresent(geometry -> {
                            stack.pushPose();
                            try {
                                var boneTransform = bone.equals("head") || bone.equals("hat")
                                        ? VehicleFirstPersonView.lookingHeadTransform(transform,
                                        bodyRotation, camera.rotation()) : transform;
                                // Same Bedrock bind-pose/additive hierarchy used
                                // by the existing SBM-based arm renderer.
                                // https://github.com/MCModderAnchor/SimpleBedrockModel
                                stack.last().normal().mul(new Matrix3f(boneTransform).invert().transpose());
                                stack.last().pose().mul(boneTransform);
                                geometry.render(stack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
                            } finally {
                                stack.popPose();
                            }
                        }));
            }
        } finally {
            stack.popPose();
        }
    }
}
