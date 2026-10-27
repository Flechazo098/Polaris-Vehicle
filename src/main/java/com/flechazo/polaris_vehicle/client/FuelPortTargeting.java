package com.flechazo.polaris_vehicle.client;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.util.OptionalOps;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionRole;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;

/** Selects visible fuel ports outside the entity's vanilla interaction bounds. */
final class FuelPortTargeting {
    private static final double QUERY_MARGIN = 8.0;
    private static final double RAY_HALF_SIZE = 1.0E-5;
    private static final double TARGET_RADIUS = 0.35;

    static Maybe<AbstractVehicleEntity> findVehicle(Minecraft client) {
        if (client.player == null || client.level == null) return Maybe.none();
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0F).normalize();
        double range = client.player.entityInteractionRange() + 1.0;
        Vec3 movement = look.scale(range);
        AABB query = new AABB(eye, eye).expandTowards(movement).inflate(QUERY_MARGIN);
        var target = client.level.getEntitiesOfClass(AbstractVehicleEntity.class, query,
                        vehicle -> !vehicle.isRemoved()).stream()
                .sorted(Comparator.comparingDouble(vehicle -> vehicle.distanceToSqr(client.player)))
                .flatMap(vehicle -> VehicleStructureModelManager.CLIENT.find(vehicle.vehicleTypeId())
                        .flatMap(model -> select(vehicle, model, eye, look, range, movement))
                        .toList().stream())
                .min(Comparator.comparingDouble(Target::distance));
        return OptionalOps.toMaybe(target).map(Target::vehicle);
    }

    private static Maybe<Target> select(AbstractVehicleEntity vehicle, VehicleCollisionModel model,
                                         Vec3 eye, Vec3 look, double range, Vec3 movement) {
        return model.locator("oil").flatMap(local -> {
            var rotation = vehicle.vehicleBodyRotation();
            Vector3f rotated = rotation.transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
            Vec3 port = vehicle.position().add(rotated.x, rotated.y, rotated.z);
            double along = port.subtract(eye).dot(look);
            if (along < 0 || along > range
                    || eye.add(look.scale(along)).distanceTo(port) > TARGET_RADIUS) return Maybe.none();
            AABB rayPoint = new AABB(eye, eye).inflate(RAY_HALF_SIZE);
            double fraction = model.sweepFractionAgainstWorldAabb(vehicle.position(), rotation, rayPoint,
                    movement.scale(-1), VehicleCollisionRole.PHYSICS.mask(), true);
            double distance = fraction * range;
            return Double.isFinite(distance) && Math.abs(distance - along) <= TARGET_RADIUS
                    ? Maybe.some(new Target(vehicle, distance)) : Maybe.none();
        });
    }

    private record Target(AbstractVehicleEntity vehicle, double distance) { }
}
