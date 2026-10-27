package com.flechazo.polaris_vehicle.vehicle.seat;

import com.flechazo.hkt.Maybe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public final class VehicleSeatSelector {
    private static final double EXACT_HALF_WIDTH = 0.34;
    private static final double EXACT_HALF_HEIGHT = 0.50;
    private static final double FAR_FALLBACK_ANGLE_RADIANS = Math.toRadians(12.0);
    private static final double NEAR_FALLBACK_ANGLE_RADIANS = Math.toRadians(50.0);
    private static final double FAR_FALLBACK_PERPENDICULAR_DISTANCE = 0.95;
    private static final double NEAR_FALLBACK_PERPENDICULAR_DISTANCE = 1.30;
    private static final double NEAR_FALLBACK_DISTANCE = 1.0;
    private static final double FAR_FALLBACK_DISTANCE = 3.0;
    private static final double DISTANCE_SCORE_WEIGHT = 0.08;
    private static final double EPSILON = 1.0E-7;

    private VehicleSeatSelector() {
    }

    public static Maybe<Selection> select(
            Vec3 rayOrigin,
            Vec3 rayDirection,
            double interactionRange,
            List<Candidate> candidates
    ) {
        if (!Double.isFinite(interactionRange) || interactionRange <= 0.0) {
            return Maybe.none();
        }
        Vec3 direction = rayDirection.normalize();
        if (direction.lengthSqr() < EPSILON) {
            return Maybe.none();
        }

        Selection exact = null;
        for (Candidate candidate : candidates) {
            double hitDistance = rayIntersectionDistance(
                    rayOrigin,
                    direction,
                    exactBounds(candidate.center())
            );
            if (!Double.isFinite(hitDistance) || hitDistance < 0.0) {
                continue;
            }
            if (exact == null || hitDistance < exact.rayDistance()) {
                exact = new Selection(
                        candidate,
                        Match.EXACT,
                        hitDistance,
                        candidate.center().distanceTo(rayOrigin)
                );
            }
        }
        if (exact != null) {
            return Maybe.some(exact);
        }

        Selection fallback = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Candidate candidate : candidates) {
            if (candidate.occupied() || candidate.obstructed()) {
                continue;
            }
            Vec3 toSeat = candidate.center().subtract(rayOrigin);
            double directDistance = toSeat.length();
            if (directDistance < EPSILON || directDistance > interactionRange) {
                continue;
            }
            double rayDistance = toSeat.dot(direction);
            if (rayDistance <= 0.0 || rayDistance > interactionRange) {
                continue;
            }
            double cosine = Math.clamp(rayDistance / directDistance, -1.0, 1.0);
            double angle = Math.acos(cosine);
            double perpendicularDistance = Math.sqrt(Math.max(
                    0.0,
                    directDistance * directDistance - rayDistance * rayDistance
            ));
            double distanceBlend = Math.clamp(
                    (directDistance - NEAR_FALLBACK_DISTANCE)
                            / (FAR_FALLBACK_DISTANCE - NEAR_FALLBACK_DISTANCE),
                    0.0,
                    1.0
            );
            double maximumAngle = NEAR_FALLBACK_ANGLE_RADIANS
                    + (FAR_FALLBACK_ANGLE_RADIANS - NEAR_FALLBACK_ANGLE_RADIANS)
                    * distanceBlend;
            double maximumPerpendicularDistance = NEAR_FALLBACK_PERPENDICULAR_DISTANCE
                    + (FAR_FALLBACK_PERPENDICULAR_DISTANCE
                    - NEAR_FALLBACK_PERPENDICULAR_DISTANCE) * distanceBlend;
            if (angle > maximumAngle
                    || perpendicularDistance > maximumPerpendicularDistance) {
                continue;
            }
            double score = angle / maximumAngle
                    + directDistance / interactionRange * DISTANCE_SCORE_WEIGHT;
            if (score < bestScore) {
                bestScore = score;
                fallback = new Selection(
                        candidate,
                        Match.ADAPTIVE_FALLBACK,
                        rayDistance,
                        directDistance
                );
            }
        }
        return Maybe.ofNullable(fallback);
    }

    private static AABB exactBounds(Vec3 center) {
        return new AABB(
                center.x - EXACT_HALF_WIDTH,
                center.y - EXACT_HALF_HEIGHT,
                center.z - EXACT_HALF_WIDTH,
                center.x + EXACT_HALF_WIDTH,
                center.y + EXACT_HALF_HEIGHT,
                center.z + EXACT_HALF_WIDTH
        );
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

    public record Candidate(int seatIndex, Vec3 center, boolean occupied, boolean obstructed) {
        public Candidate {
            if (seatIndex < 0) {
                throw new IllegalArgumentException("Seat index may not be negative");
            }
            Objects.requireNonNull(center, "center");
        }
    }

    public record Selection(
            Candidate candidate,
            Match match,
            double rayDistance,
            double targetDistance
    ) {
        public Selection {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(match, "match");
            if (!Double.isFinite(rayDistance) || rayDistance < 0.0) {
                throw new IllegalArgumentException("Ray distance must be finite and non-negative");
            }
            if (!Double.isFinite(targetDistance) || targetDistance < 0.0) {
                throw new IllegalArgumentException("Target distance must be finite and non-negative");
            }
        }
    }

    public enum Match {
        EXACT,
        ADAPTIVE_FALLBACK
    }
}
