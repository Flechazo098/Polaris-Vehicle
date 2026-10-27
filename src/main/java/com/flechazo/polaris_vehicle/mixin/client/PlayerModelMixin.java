package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    private static final float RIDER_PANTS_HORIZONTAL_SCALE = 4.0F / 4.5F;
    private static final float RIDER_PANTS_VERTICAL_SCALE = 12.0F / 12.5F;

    @Shadow
    @Final
    public ModelPart leftPants;

    @Shadow
    @Final
    public ModelPart rightPants;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void retractInflatedRiderPants(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callbackInfo
    ) {
        if (entity.getVehicle() instanceof AbstractVehicleEntity) {
            scalePants(leftPants);
            scalePants(rightPants);
        }
    }

    private static void scalePants(ModelPart pants) {
        pants.xScale *= RIDER_PANTS_HORIZONTAL_SCALE;
        pants.yScale *= RIDER_PANTS_VERTICAL_SCALE;
        pants.zScale *= RIDER_PANTS_HORIZONTAL_SCALE;
    }
}
