package com.flechazo.polaris_vehicle.vehicle.seat;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

public final class VehicleWheelSelector {
    private static final double MINIMUM_TARGET_HALF_EXTENT = 0.30;
    private static final double TARGET_MARGIN = 0.10;
    private static final double NEAR_FALLBACK_MARGIN = 0.75;
    private static final double FAR_FALLBACK_MARGIN = 0.35;
    private static final double NEAR_DISTANCE = 1.0;
    private static final double FAR_DISTANCE = 3.0;
    private static final double NEAR_ANGLE = Math.toRadians(55.0);
    private static final double FAR_ANGLE = Math.toRadians(16.0);
    private static final double APPROACH_MARGIN = 0.15;
    private static final double EPSILON = 1.0E-7;

    private VehicleWheelSelector() {
    }

    public static VehiclePartSlot interactionSlot(VehiclePartSlot physicsSlot) {
        if (!physicsSlot.isWheel()) {
            throw new IllegalArgumentException(
                    "Maintenance target is not a wheel slot: " + physicsSlot
            );
        }
        return physicsSlot;
    }

    public static Maybe<Target> select(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double interactionRange,
            double tireRadius,
            List<Target> targets
    ) {
        return select(rayOrigin, rayDirection, interactionRange, tireRadius, targets, true);
    }

    public static Maybe<Target> selectWorld(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            Vec3 bodyOrigin,
            Quaternionfc bodyRotation,
            double interactionRange,
            double tireRadius,
            List<Target> targets,
            boolean allowFallback
    ) {
        Quaternionf inverse = new Quaternionf(
                bodyRotation
        ).invert();
        Vec3 localOrigin = VehicleSeatTransform.toBodySpace(rayOrigin.subtract(bodyOrigin), inverse);
        Vec3 localDirection = VehicleSeatTransform.toBodySpace(rayDirection, inverse);
        return select(localOrigin, localDirection, interactionRange,
                tireRadius, targets, allowFallback);
    }

    private static Maybe<Target> select(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double interactionRange,
            double tireRadius,
            List<Target> targets,
            boolean allowFallback
    ) {
        if (!Double.isFinite(interactionRange) || interactionRange <= 0.0
                || !Double.isFinite(tireRadius) || tireRadius <= 0.0) {
            return Maybe.none();
        }
        Vec3 direction = rayDirection.normalize();
        if (direction.lengthSqr() < EPSILON) {
            return Maybe.none();
        }

        double halfExtent = Math.max(
                MINIMUM_TARGET_HALF_EXTENT,
                tireRadius + TARGET_MARGIN
        );
        Target exact = null;
        double nearestHit = Double.POSITIVE_INFINITY;
        for (Target target : targets) {
            if (!isExposedFrom(rayOrigin, target, targets)) {
                continue;
            }
            double hit = rayIntersectionDistance(
                    rayOrigin,
                    direction,
                    target.bounds() != null ? target.bounds() : AABB.ofSize(
                            target.center(),
                            halfExtent * 2.0,
                            halfExtent * 2.0,
                            halfExtent * 2.0
                    )
            );
            if (Double.isFinite(hit) && hit <= interactionRange && hit < nearestHit) {
                nearestHit = hit;
                exact = target;
            }
        }
        if (exact != null) {
            return Maybe.some(exact);
        }
        if (!allowFallback) {
            return Maybe.none();
        }

        Target fallback = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Target target : targets) {
            if (target.bounds() != null) continue; // Authored regions never use angular guessing.
            if (!isExposedFrom(rayOrigin, target, targets)) {
                continue;
            }
            Vec3 toTarget = target.center().subtract(rayOrigin);
            double directDistance = toTarget.length();
            if (directDistance < EPSILON || directDistance > interactionRange) {
                continue;
            }
            double alongRay = toTarget.dot(direction);
            if (alongRay <= 0.0 || alongRay > interactionRange) {
                continue;
            }
            double perpendicular = Math.sqrt(Math.max(
                    0.0,
                    directDistance * directDistance - alongRay * alongRay
            ));
            double angle = Math.acos(Math.clamp(
                    alongRay / directDistance,
                    -1.0,
                    1.0
            ));
            double blend = Math.clamp(
                    (directDistance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE),
                    0.0,
                    1.0
            );
            double maximumAngle = NEAR_ANGLE + (FAR_ANGLE - NEAR_ANGLE) * blend;
            double maximumPerpendicular = halfExtent + NEAR_FALLBACK_MARGIN
                    + (FAR_FALLBACK_MARGIN - NEAR_FALLBACK_MARGIN) * blend;
            if (angle > maximumAngle || perpendicular > maximumPerpendicular) {
                continue;
            }
            double score = perpendicular / maximumPerpendicular
                    + directDistance / interactionRange * 0.05;
            if (score < bestScore) {
                bestScore = score;
                fallback = target;
            }
        }
        return Maybe.ofNullable(fallback);
    }

    private static boolean isExposedFrom(
            Vec3 origin,
            Target target,
            List<Target> targets
    ) {
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (Target candidate : targets) {
            minimumX = Math.min(minimumX, candidate.center().x);
            maximumX = Math.max(maximumX, candidate.center().x);
            minimumZ = Math.min(minimumZ, candidate.center().z);
            maximumZ = Math.max(maximumZ, candidate.center().z);
        }
        double centerX = (minimumX + maximumX) * 0.5;
        double centerZ = (minimumZ + maximumZ) * 0.5;

        if (target.bounds() != null) {
            return !(origin.x < centerX - APPROACH_MARGIN && target.center().x > centerX + APPROACH_MARGIN)
                    && !(origin.x > centerX + APPROACH_MARGIN && target.center().x < centerX - APPROACH_MARGIN);
        }

        if (origin.x < minimumX - APPROACH_MARGIN
                && target.center().x > centerX + EPSILON) {
            return false;
        }
        if (origin.x > maximumX + APPROACH_MARGIN
                && target.center().x < centerX - EPSILON) {
            return false;
        }
        if (origin.z < minimumZ - APPROACH_MARGIN
                && target.center().z > centerZ + EPSILON) {
            return false;
        }
        return origin.z <= maximumZ + APPROACH_MARGIN
                || target.center().z >= centerZ - EPSILON;
    }

    private static double rayIntersectionDistance(Vec3 origin, Vec3 direction, AABB bounds) {
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        double[] origins = {origin.x, origin.y, origin.z};
        double[] directions = {direction.x, direction.y, direction.z};
        double[] minimums = {bounds.minX, bounds.minY, bounds.minZ};
        double[] maximums = {bounds.maxX, bounds.maxY, bounds.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < EPSILON) {
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

    public record Target(int wheelIndex, Vec3 center, AABB bounds) {

        public Target {
            if (wheelIndex < 0) {
                throw new IllegalArgumentException("wheelIndex may not be negative");
            }
            Objects.requireNonNull(center, "center");
        }
    }
}
