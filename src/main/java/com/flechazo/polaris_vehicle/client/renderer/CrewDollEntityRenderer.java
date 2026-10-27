package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.CrewDollEntityModel;
import com.flechazo.polaris_vehicle.content.entity.CrewDollEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class CrewDollEntityRenderer extends GeoEntityRenderer<CrewDollEntity> {
    private static final double WORLD_OFFSET_X = 0.0;
    private static final double WORLD_OFFSET_Y = 0.0;
    private static final double WORLD_OFFSET_Z = 0.0;
    private static final float WORLD_SCALE = 0.5F;

    public CrewDollEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CrewDollEntityModel());
        shadowRadius = 0.25F;
    }

    @Override
    public void render(
            CrewDollEntity doll,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        poseStack.pushPose();
        try {
            poseStack.translate(WORLD_OFFSET_X, WORLD_OFFSET_Y, WORLD_OFFSET_Z);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
            poseStack.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
            super.render(doll, 0.0F, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
