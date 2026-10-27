package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.VehicleCameraController;
import com.flechazo.polaris_vehicle.client.VehicleFirstPersonView;
import com.flechazo.polaris_vehicle.client.VehicleViewTransform;
import com.flechazo.polaris_vehicle.client.renderer.VehicleFirstPersonDriverBody;
import com.flechazo.polaris_vehicle.config.VehicleClientConfig;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Unique
    private UUID polarisVehicle$lookVehicle;
    @Unique
    private VehicleFirstPersonView.Look polarisVehicle$headLook =
            new VehicleFirstPersonView.Look(0, 0);

    @Shadow
    public abstract Quaternionf rotation();

    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    private float roll;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float distance, float vertical, float horizontal);

    @Shadow
    private float getMaxZoom(float requestedDistance) {
        throw new AssertionError();
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void polarisVehicle$centerDrivingCamera(
            BlockGetter level,
            Entity cameraEntity,
            boolean detached,
            boolean mirrored,
            float partialTick,
            CallbackInfo callback
    ) {
        if (!(cameraEntity instanceof LocalPlayer player)
                || !(player.getVehicle() instanceof AbstractVehicleEntity vehicle)) {
            polarisVehicle$lookVehicle = null;
            return;
        }

        if (detached) {
            polarisVehicle$lookVehicle = null;
            if (VehicleCameraController.isVehicleCameraEnabled() && vehicle.isDriver(player)) {
                double centerX = Mth.lerp(partialTick, vehicle.xo, vehicle.getX());
                double centerY = Mth.lerp(partialTick, vehicle.yo, vehicle.getY())
                        + vehicle.getBbHeight() * 0.5;
                double centerZ = Mth.lerp(partialTick, vehicle.zo, vehicle.getZ());
                setPosition(centerX, centerY, centerZ);
                move(-getMaxZoom((float) VehicleClientConfig.get().drivingCameraDistance()), 0, 0);
            }
            return;
        }

        float relativeYaw = Mth.wrapDegrees(player.getYRot() - vehicle.getYRot()
                + yRot - player.getViewYRot(partialTick));
        if (vehicle.isDriver(player) && VehicleFirstPersonDriverBody.usesDriverDummy(vehicle)) {
            if (!vehicle.getUUID().equals(polarisVehicle$lookVehicle)) {
                polarisVehicle$headLook = new VehicleFirstPersonView.Look(0, 0);
                polarisVehicle$lookVehicle = vehicle.getUUID();
            }
            var allowed = VehicleFirstPersonDriverBody.limitLook(vehicle, partialTick, polarisVehicle$headLook,
                    new VehicleFirstPersonView.Look(relativeYaw, xRot), roll);
            float yawCorrection = allowed.yaw() - relativeYaw;
            float pitchCorrection = allowed.pitch() - xRot;
            player.setYRot(player.getYRot() + yawCorrection);
            player.yRotO += yawCorrection;
            player.setYHeadRot(player.getYRot());
            player.setXRot(Mth.clamp(player.getXRot() + pitchCorrection, -90, 90));
            player.xRotO += pitchCorrection;
            yRot += yawCorrection;
            xRot = allowed.pitch();
            relativeYaw = allowed.yaw();
            polarisVehicle$headLook = allowed;
        } else {
            polarisVehicle$lookVehicle = null;
        }
        var viewRotation = VehicleViewTransform.cameraRotation(vehicle.vehicleBodyRenderRotation(partialTick),
                relativeYaw, xRot, roll);
        rotation().set(viewRotation);
        forwards.set(0, 0, -1).rotate(viewRotation);
        up.set(0, 1, 0).rotate(viewRotation);
        left.set(-1, 0, 0).rotate(viewRotation);

        var eye = vehicle.isDriver(player) && VehicleFirstPersonDriverBody.usesDriverDummy(vehicle)
                ? VehicleFirstPersonDriverBody.eyeRenderPosition(vehicle, partialTick, rotation())
                .orElse(vehicle.riderEyeRenderPosition(player, partialTick))
                : VehicleFirstPersonDriverBody.passengerEyeRenderPosition(vehicle, player, partialTick, rotation());
        setPosition(eye.x, eye.y, eye.z);
    }
}
