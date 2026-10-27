package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.model.RampAoLighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(QuadLighter.class)
public abstract class QuadLighterMixin {
    @Redirect(
            method = "process",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData("
                            + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                            + "Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[IIZ)V"
            )
    )
    private void polarisVehicle$applySlopeAo(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            BakedQuad quad,
            float[] brightness,
            float red,
            float green,
            float blue,
            float alpha,
            int[] lightmap,
            int packedOverlay,
            boolean readAlpha
    ) {
        consumer.putBulkData(
                pose,
                quad,
                RampAoLighting.appliedCopy(quad, brightness),
                red,
                green,
                blue,
                alpha,
                lightmap,
                packedOverlay,
                readAlpha
        );
    }
}
