package com.flechazo.polaris_vehicle.client;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.collision.*;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleCollisionDebugRenderer {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final double MAXIMUM_RENDER_DISTANCE_SQUARED = 128.0 * 128.0;

    private static DebugMode mode = DebugMode.OFF;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null || !VehicleKeyMappings.COLLISION_DEBUG.consumeClick()) {
            return;
        }
        mode = mode.next();
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.translatable(mode.translationKey),
                    true
            );
        }
        PolarisVehicle.LOGGER.info(
                "[vehicle-diagnostics] collision_debug mode={}",
                mode
        );
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (mode == DebugMode.OFF
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractVehicleEntity vehicle)
                    || entity.distanceToSqr(cameraPosition) > MAXIMUM_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            VehicleStructureModelManager.CLIENT.find(vehicle.vehicleTypeId())
                    .ifPresent(model -> renderVehicle(
                            poseStack,
                            lines,
                            cameraPosition,
                            partialTick,
                            vehicle,
                            model,
                            event
                    ));
        }
        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderVehicle(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 cameraPosition,
            float partialTick,
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            RenderLevelStageEvent event
    ) {
        Vec3 renderPosition = new Vec3(
                Mth.lerp(partialTick, vehicle.xOld, vehicle.getX()),
                Mth.lerp(partialTick, vehicle.yOld, vehicle.getY()),
                Mth.lerp(partialTick, vehicle.zOld, vehicle.getZ())
        );
        Quaternionf renderRotation = vehicle.vehicleBodyRenderRotation(partialTick);
        AABB visibilityBounds = model.worldBounds(renderPosition, renderRotation);
        if (!event.getFrustum().isVisible(visibilityBounds)) {
            return;
        }

        renderAabb(
                poseStack,
                lines,
                model.physicsWorldBounds(renderPosition, renderRotation),
                cameraPosition,
                0.0F, 1.0F, 1.0F, 1.0F
        );
        if (mode == DebugMode.ALL) {
            renderAabb(
                    poseStack,
                    lines,
                    visibilityBounds,
                    cameraPosition,
                    1.0F, 1.0F, 1.0F, 1.0F
            );
        }

        for (VehicleCollisionCube cube : model.cubes()) {
            if (mode == DebugMode.PHYSICS
                    && !cube.participatesIn(VehicleCollisionRole.PHYSICS.mask(), true)) {
                continue;
            }
            renderObb(
                    poseStack.last(),
                    lines,
                    cube.box().toWorld(renderPosition, renderRotation),
                    cameraPosition,
                    cube.role()
            );
        }
    }

    private static void renderAabb(
            PoseStack poseStack,
            VertexConsumer lines,
            AABB box,
            Vec3 camera,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                box.move(-camera.x, -camera.y, -camera.z),
                red,
                green,
                blue,
                alpha
        );
    }

    private static void renderObb(
            PoseStack.Pose pose,
            VertexConsumer lines,
            OrientedBoundingBox box,
            Vec3 camera,
            VehicleCollisionRole role
    ) {
        float red;
        float green;
        float blue;
        switch (role) {
            case PHYSICS -> {
                red = 0.15F;
                green = 1.0F;
                blue = 0.15F;
            }
            case HIT_ONLY -> {
                red = 1.0F;
                green = 0.55F;
                blue = 0.0F;
            }
            case DECORATION -> {
                red = 0.35F;
                green = 0.55F;
                blue = 1.0F;
            }
            case SENSOR -> {
                red = 1.0F;
                green = 0.1F;
                blue = 1.0F;
            }
            default -> throw new IllegalStateException("Unhandled collision role " + role);
        }
        for (int[] edge : BOX_EDGES) {
            writeLine(
                    pose,
                    lines,
                    box.corner(edge[0]).subtract(camera),
                    box.corner(edge[1]).subtract(camera),
                    red,
                    green,
                    blue
            );
        }
    }

    private static void writeLine(
            PoseStack.Pose pose,
            VertexConsumer lines,
            Vec3 start,
            Vec3 end,
            float red,
            float green,
            float blue
    ) {
        Vec3 direction = end.subtract(start);
        double length = direction.length();
        if (length <= 1.0E-9) {
            return;
        }
        float normalX = (float) (direction.x / length);
        float normalY = (float) (direction.y / length);
        float normalZ = (float) (direction.z / length);
        lines.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
        lines.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private enum DebugMode {
        OFF("message.polaris_vehicle.collision_debug.off"),
        PHYSICS("message.polaris_vehicle.collision_debug.physics"),
        ALL("message.polaris_vehicle.collision_debug.all");

        private final String translationKey;

        DebugMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private DebugMode next() {
            return switch (this) {
                case OFF -> PHYSICS;
                case PHYSICS -> ALL;
                case ALL -> OFF;
            };
        }
    }
}
