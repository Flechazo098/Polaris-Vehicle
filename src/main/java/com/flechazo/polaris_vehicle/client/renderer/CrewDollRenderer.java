package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.CrewDollModel;
import com.flechazo.polaris_vehicle.client.model.ModelBounds;
import com.flechazo.polaris_vehicle.item.CrewDollItem;
import com.flechazo.polaris_vehicle.item.CrewDollVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public final class CrewDollRenderer extends GeoItemRenderer<CrewDollItem> {
    private static final float ITEM_SCALE = 0.65F;
    private static final double GUI_OFFSET_X = 0.25;
    private static final double GUI_OFFSET_Y = -0.25;
    private static final double GUI_OFFSET_Z = 0.0;
    private static final double NON_GUI_OFFSET_X = 0.0;
    private static final double NON_GUI_OFFSET_Y = 0.0;
    private static final double NON_GUI_OFFSET_Z = 0.0;
    private BakedGeoModel measuredModel;
    private ModelBounds measuredBounds;
    private BakedGeoModel renderingModel;

    public CrewDollRenderer() {
        super(new CrewDollModel());
        withScale(ITEM_SCALE);
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            CrewDollItem animatable,
            BakedGeoModel model,
            @Nullable MultiBufferSource bufferSource,
            @Nullable VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        renderingModel = model;
        if (ItemPreviewRenderContext.isActive()) {
            this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
            if (isReRender) {
                return;
            }
            ModelBounds bounds = previewBounds(model);
            float scale = bounds.isEmpty() ? ITEM_SCALE : 1.0F / bounds.longestEdge();
            scaleModelForRender(
                    scale, scale, poseStack, animatable, model, false,
                    partialTick, packedLight, packedOverlay
            );
            poseStack.translate(0.5F / scale, 0.51F / scale, 0.5F / scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            if (!bounds.isEmpty()) {
                poseStack.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            }
            return;
        }
        if (renderPerspective == ItemDisplayContext.GUI && animatable.variant() == CrewDollVariant.YELLOW_DUCK) {
            this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
            if (isReRender) return;
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            return;
        }
        super.preRender(
                poseStack,
                animatable,
                model,
                bufferSource,
                buffer,
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                colour
        );
        if (renderPerspective == ItemDisplayContext.GUI) {
            poseStack.translate(GUI_OFFSET_X, GUI_OFFSET_Y, GUI_OFFSET_Z);
        } else {
            poseStack.translate(NON_GUI_OFFSET_X, NON_GUI_OFFSET_Y, NON_GUI_OFFSET_Z);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CrewDollItem animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (renderPerspective == ItemDisplayContext.GUI && animatable.variant() == CrewDollVariant.YELLOW_DUCK
                && bone.getName().equals("bone")) {
            ModelBounds posed = ModelBounds.of(renderingModel, 0, 0);
            poseStack.pushPose();
            try {
                if (!posed.isEmpty()) poseStack.translate(-posed.centerX(), -posed.centerY(), -posed.centerZ());
                super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                        isReRender, partialTick, packedLight, packedOverlay, colour);
            } finally {
                poseStack.popPose();
            }
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    private ModelBounds previewBounds(BakedGeoModel model) {
        if (measuredModel != model) {
            measuredModel = model;
            measuredBounds = ModelBounds.of(model, 0.0F, 0.0F);
        }
        return measuredBounds;
    }
}
