package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.VehicleViewTransform;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.FirstPersonGeometry;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleFirstPersonArms {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final float BLOCKBENCH_FILM_GAUGE = 35.0F;
    private static final float BLOCKBENCH_FOCAL_LENGTH = 18.0F;
    private static final float BLOCKBENCH_ASPECT_RATIO = 16.0F / 9.0F;
    private static final float BLOCKBENCH_VERTICAL_COTANGENT =
            2.0F * BLOCKBENCH_FOCAL_LENGTH
                    / (BLOCKBENCH_FILM_GAUGE / BLOCKBENCH_ASPECT_RATIO);
    private static final float BLOCKBENCH_HORIZONTAL_COTANGENT =
            BLOCKBENCH_VERTICAL_COTANGENT / BLOCKBENCH_ASPECT_RATIO;
    private static final Matrix4f BLOCKBENCH_ATTACHABLE_FIRST_TRANSFORM =
            createBlockbenchAttachableFirstTransform();

    private VehicleFirstPersonArms() {
    }

    @SubscribeEvent
    public static void renderDrivingArms(RenderHandEvent event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null
                || !(player.getVehicle() instanceof AbstractVehicleEntity vehicle)
                || !vehicle.isDriver(player)) {
            return;
        }

        event.setCanceled(true);
        if (VehicleFirstPersonDriverBody.usesDriverDummy(vehicle)
                || event.getHand() != InteractionHand.MAIN_HAND || player.isInvisible()) {
            return;
        }

        var skeleton = VehicleHumanoidAnimations.INSTANCE;
        VertexConsumer consumer = event.getMultiBufferSource().getBuffer(
                RenderType.entityTranslucent(player.getSkin().texture())
        );

        // Follow SimpleBedrockModel's bind-pose -> additive animation -> global
        // bone transform pipeline. Its Deagle example also cancels vanilla view
        // bob before drawing a camera-relative model.
        // These authored bones contain the arm cubes themselves; rendering a
        // PlayerRenderer arm here would apply the vanilla arm pivot a second time.
        // https://github.com/MCModderAnchor/SimpleBedrockModel
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        try {
            cancelVanillaHandSway(stack, player, event.getPartialTick());
            cancelVanillaViewBob(stack, player, event.getPartialTick());
            stack.mulPose(VehicleViewTransform.handCorrection(minecraft.gameRenderer.getMainCamera().rotation(),
                    vehicle.vehicleBodyRenderRotation(event.getPartialTick())));
            matchBlockbenchProjection(stack);
            mulMatrix(stack, BLOCKBENCH_ATTACHABLE_FIRST_TRANSFORM);
            renderBone(skeleton, "rightArm", stack, consumer, event.getPackedLight());
            renderBone(skeleton, "leftArm", stack, consumer, event.getPackedLight());
        } finally {
            stack.popPose();
        }
    }

    /**
     * Inverse of ItemInHandRenderer's small mouse-lag rotations, in reverse order.
     */
    private static void cancelVanillaHandSway(PoseStack stack, LocalPlayer player,
                                              float partialTick) {
        float xBob = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float yBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        stack.mulPose(Axis.YP.rotationDegrees(-(player.getViewYRot(partialTick) - yBob) * 0.1F));
        stack.mulPose(Axis.XP.rotationDegrees(-(player.getViewXRot(partialTick) - xBob) * 0.1F));
    }

    private static void matchBlockbenchProjection(PoseStack poseStack) {
        Matrix4f gameProjection = RenderSystem.getProjectionMatrix();
        float gameHorizontalCotangent = gameProjection.m00();
        float gameVerticalCotangent = gameProjection.m11();
        if (!Float.isFinite(gameHorizontalCotangent)
                || !Float.isFinite(gameVerticalCotangent)
                || Math.abs(gameHorizontalCotangent) < 1.0E-6F
                || Math.abs(gameVerticalCotangent) < 1.0E-6F) {
            return;
        }
        poseStack.scale(
                BLOCKBENCH_HORIZONTAL_COTANGENT / gameHorizontalCotangent,
                BLOCKBENCH_VERTICAL_COTANGENT / gameVerticalCotangent,
                1.0F
        );
    }

    private static Matrix4f createBlockbenchAttachableFirstTransform() {
        float scale = 1.0F / PIXELS_PER_BLOCK;
        return new Matrix4f()
                .lookAt(
                        0.0F, 19.0F * scale, -40.0F * scale,
                        0.0F, 16.0F * scale, 0.0F,
                        0.0F, 1.0F, 0.0F
                )
                .translate(-8.0F * scale, 6.0F * scale, -18.0F * scale)
                .rotateZ(115.0F * Mth.DEG_TO_RAD)
                .rotateY(45.0F * Mth.DEG_TO_RAD)
                .rotateX(-95.0F * Mth.DEG_TO_RAD);
    }

    /**
     * Exact inverse of {@code GameRenderer.bobView}, in the same multiplication
     * order used by SimpleBedrockModel's first-person renderer.
     */
    private static void cancelVanillaViewBob(
            PoseStack poseStack,
            LocalPlayer player,
            float partialTick
    ) {
        if (!Minecraft.getInstance().options.bobView().get()) {
            return;
        }
        float walkDelta = player.walkDist - player.walkDistO;
        float walkPhase = -(player.walkDist + walkDelta * partialTick);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        poseStack.mulPose(Axis.XN.rotationDegrees(
                Math.abs(Mth.cos(walkPhase * Mth.PI - 0.2F) * bob) * 5.0F
        ));
        poseStack.mulPose(Axis.ZN.rotationDegrees(
                Mth.sin(walkPhase * Mth.PI) * bob * 3.0F
        ));
        poseStack.translate(
                -Mth.sin(walkPhase * Mth.PI) * bob * 0.5F,
                Math.abs(Mth.cos(walkPhase * Mth.PI) * bob),
                0.0F
        );
    }

    private static void renderBone(
            VehicleHumanoidAnimations skeleton,
            String boneName,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight
    ) {
        skeleton.firstPersonBoneTransform(boneName).ifPresent(transform ->
                skeleton.firstPersonGeometry(boneName).ifPresent(geometry ->
                        renderGeometry(poseStack, transform, geometry, consumer, packedLight)
                )
        );
    }

    private static void renderGeometry(
            PoseStack poseStack,
            Matrix4f boneTransform,
            FirstPersonGeometry geometry,
            VertexConsumer consumer,
            int packedLight
    ) {
        poseStack.pushPose();
        try {
            mulMatrix(poseStack, boneTransform);
            geometry.render(
                    poseStack,
                    consumer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Same pose/normal update performed by SBM's {@code MathUtil.mulMatrix}.
     */
    private static void mulMatrix(PoseStack poseStack, Matrix4f transform) {
        poseStack.last().normal().mul(new Matrix3f(transform).invert().transpose());
        poseStack.last().pose().mul(transform);
    }
}
