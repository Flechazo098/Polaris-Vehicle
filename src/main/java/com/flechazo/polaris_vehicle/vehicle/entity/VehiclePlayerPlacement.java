package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.hkt.Maybe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

final class VehiclePlayerPlacement {

    static Maybe<Vec3> findSafePosition(
            Vec3 playerPosition, AABB playerBounds, AABB vehicleBounds,
            Predicate<AABB> overlapsVehicle, Predicate<AABB> safeDestination
    ) {
        if (!overlapsVehicle.test(playerBounds)) {
            return Maybe.some(playerPosition);
        }
        double halfWidth = Math.max(playerBounds.getXsize(), playerBounds.getZsize()) * 0.5;
        double margin = halfWidth + 0.08;
        double minX = vehicleBounds.minX - margin;
        double maxX = vehicleBounds.maxX + margin;
        double minZ = vehicleBounds.minZ - margin;
        double maxZ = vehicleBounds.maxZ + margin;
        double x = Math.clamp(playerPosition.x, vehicleBounds.minX, vehicleBounds.maxX);
        double z = Math.clamp(playerPosition.z, vehicleBounds.minZ, vehicleBounds.maxZ);
        List<Vec3> sides = List.of(
                new Vec3(minX, playerPosition.y, z), new Vec3(maxX, playerPosition.y, z),
                new Vec3(x, playerPosition.y, minZ), new Vec3(x, playerPosition.y, maxZ),
                new Vec3(minX, playerPosition.y, minZ), new Vec3(minX, playerPosition.y, maxZ),
                new Vec3(maxX, playerPosition.y, minZ), new Vec3(maxX, playerPosition.y, maxZ)
        );
        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        // Nearby slabs and a one-block step are eligible, but there is no unsupported roof escape.
        for (Vec3 side : sides) {
            for (double height : new double[]{0.0, 0.5, -0.5, 1.0, -1.0}) {
                Vec3 candidate = side.add(0.0, height, 0.0);
                AABB bounds = playerBounds.move(candidate.subtract(playerPosition));
                double distance = candidate.distanceToSqr(playerPosition);
                if (distance < bestDistance && !overlapsVehicle.test(bounds)
                        && safeDestination.test(bounds)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Maybe.ofNullable(best);
    }
}
