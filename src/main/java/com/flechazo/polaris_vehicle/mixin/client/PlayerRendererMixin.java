package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleSeatLayout;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.seat.VehicleSeatTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    private static final float VANILLA_PLAYER_RENDER_SCALE = 15.0F / 16.0F;

    @Inject(
            method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("TAIL")
    )
    private void restoreVehicleRiderBlockbenchScale(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float partialTick,
            CallbackInfo callbackInfo
    ) {
        if (player.getVehicle() instanceof AbstractVehicleEntity) {
            float inverseVanillaScale = 1.0F / VANILLA_PLAYER_RENDER_SCALE;
            poseStack.scale(
                    inverseVanillaScale,
                    inverseVanillaScale,
                    inverseVanillaScale
            );
        }
    }

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("TAIL")
    )
    private void alignRiderWithVehicleBody(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float renderedBodyYaw,
            float partialTick,
            float scale,
            CallbackInfo callbackInfo
    ) {
        if (player.getVehicle() instanceof AbstractVehicleEntity vehicle) {
            poseStack.mulPose(VehicleSeatTransform.riderRenderCorrection(
                    vehicle.vehicleBodyRenderRotation(partialTick),
                    renderedBodyYaw
            ));
            boolean driver = vehicle.isDriver(player);
            VehicleHumanoidAnimations.INSTANCE.riderVehicleFramePivot(vehicle.vehicleTypeId())
                    .ifPresent(pivot -> {
                        var seat = VehicleSeatLayout.require(vehicle.vehicleTypeId()).seats().getFirst();
                        var attachment = player.getAttachments().get(EntityAttachment.VEHICLE, 0, 0);
                        var translation = VehicleSeatTransform.authoredRiderRenderTranslation(
                                pivot, seat, attachment, scale);
                        poseStack.translate(driver ? translation.x : 0.0, translation.y, translation.z);
                    });
        }
    }
}
