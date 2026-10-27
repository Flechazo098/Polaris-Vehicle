package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.ModelBounds;
import com.flechazo.polaris_vehicle.client.model.StaticGeoItemModel;
import com.flechazo.polaris_vehicle.item.StaticGeoAsset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class StaticGeoItemRenderer<T extends Item & GeoAnimatable & StaticGeoAsset>
        extends GeoItemRenderer<T> {
    private static final float GUI_FILL = 0.94F;
    private static final float HAND_EDGE = 1.0F;
    private static final float GUI_PITCH = 45.0F;
    private static final float GUI_YAW = -45.0F;
    private static final float DEGENERATE_EXTENT = 1.0E-4F;
    private BakedGeoModel measuredModel;
    private ModelBounds measuredBounds;
    public StaticGeoItemRenderer() {
        this(new StaticGeoItemModel<>());
    }
    protected StaticGeoItemRenderer(GeoModel<T> model) {
        super(model);
        useAlternateGuiLighting();
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext transformType,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (usesGuiSprite() && transformType == ItemDisplayContext.GUI
                && GuiSpriteItemRenderer.render(
                stack, poseStack, bufferSource, packedLight, packedOverlay)) {
            return;
        }
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }

    protected boolean usesGuiSprite() {
        return true;
    }

    @Override
    public void preRender(
            PoseStack poseStack,
            T animatable,
            BakedGeoModel model,
            @Nullable MultiBufferSource bufferSource,
            @Nullable VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
        if (isReRender) {
            return;
        }

        boolean gui = renderPerspective == ItemDisplayContext.GUI;
        boolean held = renderPerspective != null && (renderPerspective.firstPerson()
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || renderPerspective == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
        if (held && animatable.usesAuthoredHandDisplay()) {
            poseStack.translate(0.5F, 0.51F, 0.5F);
            return;
        }

        if (animatable.usesAuthoredHandPose()) {
            restoreRestPose(model);
        }

        prepareModel(animatable, model);

        ModelBounds bounds = boundsOf(model);
        if (gui && !bounds.isEmpty()) {
            float halfSlot = GUI_FILL / 2.0F;
            float scale = Float.MAX_VALUE;
            if (bounds.tiltedHalfWidth() > DEGENERATE_EXTENT) {
                scale = Math.min(scale, halfSlot / bounds.tiltedHalfWidth());
            }
            if (bounds.tiltedHalfHeight() > DEGENERATE_EXTENT) {
                scale = Math.min(scale, halfSlot / bounds.tiltedHalfHeight());
            }
            if (scale == Float.MAX_VALUE) {
                scale = 1.0F;
            }
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(GUI_PITCH));
            poseStack.mulPose(Axis.YP.rotationDegrees(GUI_YAW));
            poseStack.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
            return;
        }

        if (ItemPreviewRenderContext.isActive() && !bounds.isEmpty()) {
            float scale = HAND_EDGE / bounds.longestEdge();
            scaleModelForRender(
                    scale, scale, poseStack, animatable, model, false,
                    partialTick, packedLight, packedOverlay
            );
            poseStack.translate(
                    0.5F / scale - bounds.centerX(),
                    0.51F / scale - bounds.centerY(),
                    0.5F / scale - bounds.centerZ()
            );
            return;
        }

        if (held) {
            float scale = 1.0F;
            if (!bounds.isEmpty() && bounds.longestEdge() > DEGENERATE_EXTENT) {
                scale = Math.min(1.0F, HAND_EDGE / bounds.longestEdge());
            }
            scaleModelForRender(
                    scale, scale, poseStack, animatable, model, false,
                    partialTick, packedLight, packedOverlay
            );
            if (bounds.isEmpty()) {
                poseStack.translate(0.5F / scale, 0.51F / scale, 0.5F / scale);
            } else {
                poseStack.translate(
                        0.5F / scale - bounds.centerX(),
                        0.51F / scale - bounds.centerY(),
                        0.5F / scale - bounds.centerZ()
                );
            }
            return;
        }

        float scale = bounds.isEmpty() ? 1.0F : HAND_EDGE / bounds.longestEdge();
        scaleModelForRender(
                scale, scale, poseStack, animatable, model, false, partialTick, packedLight, packedOverlay
        );
        poseStack.translate(0.5F / scale, 0.51F / scale, 0.5F / scale);
    }

    protected void prepareModel(T animatable, BakedGeoModel model) {
    }

    private static void restoreRestPose(BakedGeoModel model) {
        for (GeoBone bone : model.topLevelBones()) {
            restoreRestPose(bone);
        }
    }

    private static void restoreRestPose(GeoBone bone) {
        BoneSnapshot rest = bone.getInitialSnapshot();
        bone.setRotX(rest.getRotX());
        bone.setRotY(rest.getRotY());
        bone.setRotZ(rest.getRotZ());
        bone.setPosX(rest.getOffsetX());
        bone.setPosY(rest.getOffsetY());
        bone.setPosZ(rest.getOffsetZ());
        bone.setScaleX(rest.getScaleX());
        bone.setScaleY(rest.getScaleY());
        bone.setScaleZ(rest.getScaleZ());
        for (GeoBone child : bone.getChildBones()) {
            restoreRestPose(child);
        }
    }

    private ModelBounds boundsOf(BakedGeoModel model) {
        if (measuredModel != model) {
            measuredBounds = measureBounds(model);
            measuredModel = model;
        }
        return measuredBounds;
    }
    protected ModelBounds measureBounds(BakedGeoModel model) {
        return ModelBounds.of(model, GUI_PITCH, GUI_YAW);
    }
}
