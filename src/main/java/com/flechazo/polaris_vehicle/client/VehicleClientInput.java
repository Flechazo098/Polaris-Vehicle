package com.flechazo.polaris_vehicle.client;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.util.OptionalOps;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.hud.VehicleTelemetryOverlay;
import com.flechazo.polaris_vehicle.client.model.VehicleTireSteeringLimits;
import com.flechazo.polaris_vehicle.client.screen.VehicleParametersScreen;
import com.flechazo.polaris_vehicle.config.VehicleClientConfig;
import com.flechazo.polaris_vehicle.item.VehicleFuelTankItem;
import com.flechazo.polaris_vehicle.item.VehicleTireItem;
import com.flechazo.polaris_vehicle.item.VehicleWrenchItem;
import com.flechazo.polaris_vehicle.network.*;
import com.flechazo.polaris_vehicle.registry.ModItemTags;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleClientInput {
    private static int lastVehicleId = -1;
    private static int heartbeatTicks;
    private static boolean lastForward;
    private static boolean lastBackward;
    private static boolean lastLeft;
    private static boolean lastRight;
    private static boolean lastBrake;
    private static int lastHornVehicleId = -1;
    private static boolean lastHorn;
    private static int lastMountedVehicleId = -1;

    private static void showDismountHint(Minecraft client) {
        int vehicleId = client.player.getVehicle() instanceof AbstractVehicleEntity vehicle ? vehicle.getId() : -1;
        if (vehicleId != -1 && vehicleId != lastMountedVehicleId) {
            client.gui.setOverlayMessage(Component.translatable("message.polaris_vehicle.dismount.mounted",
                    client.options.keyShift.getTranslatedKeyMessage()), false);
        }
        lastMountedVehicleId = vehicleId;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        var held = client.player.getItemInHand(event.getHand());
        if (client.level != null && event.getHand() == InteractionHand.MAIN_HAND
                && client.player.getVehicle() == null
                && (held.isEmpty() || held.getItem() instanceof VehicleTireItem
                || held.getItem() instanceof VehicleWrenchItem)) {
            var target = OptionalOps.toMaybe(client.level.getEntitiesOfClass(AbstractVehicleEntity.class,
                            client.player.getBoundingBox().inflate(client.player.entityInteractionRange() + 3),
                            vehicle -> vehicle.isAlive() && vehicle.hasWheelInteractionTarget(client.player))
                    .stream().min(Comparator.comparingDouble(vehicle -> vehicle.distanceToSqr(client.player))));
            if (target.isDefined()) {
                boolean exactVanillaTarget = client.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() == target.get();
                boolean otherEntity = client.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() != target.get();
                if (!exactVanillaTarget && !otherEntity) {
                    new VehicleWheelInteractionPacket(target.get().getId())
                            .sendToServer();
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (event.getHand() == InteractionHand.MAIN_HAND
                && client.player.getVehicle() == null
                && !ModItemTags.holdsVehicleTool(client.player)
                && !(client.player.getItemInHand(event.getHand()).getItem()
                instanceof VehicleFuelTankItem)
                && !(client.hitResult instanceof EntityHitResult)) {
            boolean requestedBoarding = Pathway.maybe(findVehicleThroughNonCollidingFoliage(client))
                    .peek(vehicle -> new VehicleBoardingRequestPacket(vehicle.getId()).sendToServer())
                    .peek(vehicle -> event.setCanceled(true))
                    .run().isDefined();
            if (requestedBoarding) return;
        }

        if (!(client.player.getItemInHand(event.getHand()).getItem()
                instanceof VehicleFuelTankItem)) {
            return;
        }
        boolean vanillaWillHandleVehicle = client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof AbstractVehicleEntity;
        if (event.getHand() == InteractionHand.MAIN_HAND && !vanillaWillHandleVehicle) {
            FuelPortTargeting.findVehicle(client).ifPresent(vehicle ->
                    new VehicleFuelingRequestPacket(vehicle.getId()).sendToServer());
        }
    }

    private static Maybe<AbstractVehicleEntity> findVehicleThroughNonCollidingFoliage(
            Minecraft client
    ) {
        if (client.level == null || client.player == null) {
            return Maybe.none();
        }
        Vec3 eye = client.player.getEyePosition();
        Vec3 direction = client.player.getViewVector(1.0F).normalize();
        double range = client.player.entityInteractionRange();
        Vec3 end = eye.add(direction.scale(range));
        BlockHitResult blockingBlock = client.level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));
        double blockDistance = blockingBlock.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(blockingBlock.getLocation())
                : Double.POSITIVE_INFINITY;
        AABB search = client.player.getBoundingBox()
                .expandTowards(direction.scale(range))
                .inflate(1.0);
        AbstractVehicleEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (AbstractVehicleEntity vehicle : client.level.getEntitiesOfClass(
                AbstractVehicleEntity.class,
                search,
                AbstractVehicleEntity::isAlive
        )) {
            double distance = rayAabbDistance(eye, direction, vehicle.getBoundingBox().inflate(0.05));
            if (Double.isFinite(distance)
                    && distance <= range
                    && distance <= blockDistance + 1.0E-4
                    && distance < nearestDistance) {
                nearest = vehicle;
                nearestDistance = distance;
            }
        }
        return Maybe.ofNullable(nearest);
    }

    private static double rayAabbDistance(Vec3 origin, Vec3 direction, AABB box) {
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        double[] origins = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] minimums = {box.minX, box.minY, box.minZ};
        double[] maximums = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-8) {
                if (origins[axis] < minimums[axis] || origins[axis] > maximums[axis]) {
                    return Double.NaN;
                }
                continue;
            }
            double first = (minimums[axis] - origins[axis]) / directions[axis];
            double second = (maximums[axis] - origins[axis]) / directions[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) {
                return Double.NaN;
            }
        }
        return far >= 0.0 ? near : Double.NaN;
    }

    @SubscribeEvent
    public static void onDrivingKey(InputEvent.Key event) {
        var client = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS
                && client.options.keyShift.matches(event.getKey(), event.getScanCode())) sendDismountPress(client);
        pollDrivingInput(false);
    }

    @SubscribeEvent
    public static void onDismountMouse(InputEvent.MouseButton.Post event) {
        var client = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS
                && client.options.keyShift.matchesMouse(event.getButton())) sendDismountPress(client);
    }

    private static void sendDismountPress(Minecraft client) {
        if (client.screen == null && client.player != null
                && client.player.getVehicle() instanceof AbstractVehicleEntity vehicle) {
            new VehicleDismountPressPacket(vehicle.getId()).sendToServer();
        }
    }

    @SubscribeEvent
    public static void onDrivingTickStart(ClientTickEvent.Pre event) {
        pollDrivingInput(false);
    }

    private static void pollDrivingInput(boolean heartbeat) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null
                || !(client.player.getVehicle() instanceof AbstractVehicleEntity vehicle)
                || !vehicle.isDriver(client.player)) return;
        boolean forward = client.options.keyUp.isDown();
        boolean backward = client.options.keyDown.isDown();
        boolean left = client.options.keyLeft.isDown();
        boolean right = client.options.keyRight.isDown();
        boolean brake = VehicleKeyMappings.BRAKE.isDown();
        boolean changedVehicle = lastVehicleId != vehicle.getId();
        boolean changedInput = forward != lastForward || backward != lastBackward
                || left != lastLeft || right != lastRight || brake != lastBrake;
        if (heartbeat) heartbeatTicks++;
        if (changedVehicle || changedInput || heartbeatTicks >= 10) {
            new VehicleInputPacket(forward, backward, left, right, brake,
                    calibratedSteeringResponse(vehicle)).sendToServer();
            lastVehicleId = vehicle.getId();
            lastForward = forward;
            lastBackward = backward;
            lastLeft = left;
            lastRight = right;
            lastBrake = brake;
            heartbeatTicks = 0;
        }
        boolean horn = VehicleKeyMappings.HORN.isDown();
        if (horn != lastHorn || (horn && lastHornVehicleId != vehicle.getId())) {
            new VehicleHornPacket(horn).sendToServer();
            lastHorn = horn;
            lastHornVehicleId = vehicle.getId();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            reset();
            return;
        }
        if (client.screen == null && VehicleKeyMappings.VEHICLE_TELEMETRY.consumeClick()) {
            boolean enabled = VehicleTelemetryOverlay.toggle();
            client.player.displayClientMessage(
                    Component.translatable(enabled
                            ? "message.polaris_vehicle.vehicle_telemetry.on"
                            : "message.polaris_vehicle.vehicle_telemetry.off"),
                    true
            );
        }
        if (client.screen == null && VehicleKeyMappings.VEHICLE_PARAMETERS.consumeClick()) {
            if (!VehicleClientConfig.get().parameterEditorEnabled()) {
                client.player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.vehicle_parameters.disabled"), true);
            } else {
                reset();
                client.setScreen(new VehicleParametersScreen(null));
                return;
            }
        }
        showDismountHint(client);
        if (!(client.player.getVehicle() instanceof AbstractVehicleEntity vehicle)
                || !vehicle.isDriver(client.player)) {
            reset();
            return;
        }
        if (client.screen != null) {
            releaseActiveInput(vehicle);
            return;
        }

        pollDrivingInput(true);

        if (VehicleKeyMappings.ENGINE_TOGGLE.consumeClick()) {
            new VehicleEngineTogglePacket(true).sendToServer();
        }
        if (VehicleKeyMappings.BOOST.consumeClick()) {
            new VehicleBoostRequestPacket(true).sendToServer();
        }
    }

    private static void reset() {
        lastVehicleId = -1;
        heartbeatTicks = 0;
        lastForward = false;
        lastBackward = false;
        lastLeft = false;
        lastRight = false;
        lastBrake = false;
        lastHorn = false;
        lastHornVehicleId = -1;
    }

    private static void releaseActiveInput(AbstractVehicleEntity vehicle) {
        if (lastVehicleId == vehicle.getId()
                && (lastForward || lastBackward || lastLeft || lastRight || lastBrake)) {
            new VehicleInputPacket(false, false, false, false, false,
                    calibratedSteeringResponse(vehicle)).sendToServer();
        }
        if (lastHorn && lastHornVehicleId == vehicle.getId()) {
            new VehicleHornPacket(false).sendToServer();
        }
        reset();
    }

    private static VehicleSteeringResponse
    calibratedSteeringResponse(AbstractVehicleEntity vehicle) {
        double fullLock = Math.toRadians(VehicleTypeParameters
                .resolve(vehicle.vehicleTypeId()).physics().maximumSteeringAngleDegrees());
        return VehicleTireSteeringLimits.INSTANCE
                .responseForVisibleLock(vehicle.vehicleTypeId(), fullLock, VehicleClientConfig.get().steeringResponse());
    }
}
