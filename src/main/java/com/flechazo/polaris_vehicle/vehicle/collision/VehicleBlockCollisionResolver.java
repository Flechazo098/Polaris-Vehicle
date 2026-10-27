package com.flechazo.polaris_vehicle.vehicle.collision;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.content.block.RampShape;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/**
 * Resolves a rotated, full-cube vehicle model against
 * {@link VoxelShape} collision geometry using continuous SAT. Axis resolution
 * follows vanilla's Y-first and shortest-horizontal-axis-first ordering, so a
 * blocked component is removed while tangential movement remains available.
 */
public final class VehicleBlockCollisionResolver {
    private static final double MOVEMENT_EPSILON = 1.0E-7;
    private static final double COLLISION_SKIN = 2.0E-6;
    private static final double STEP_EPSILON = 1.0E-5;
    private static final float MAXIMUM_YAW_SAMPLE_DEGREES = 2.0F;
    private static final int YAW_BINARY_SEARCH_STEPS = 10;
    private static final int MAXIMUM_STAIR_TIERS = 8;
    private static final double MINIMUM_STAIR_TREAD_RUN = 0.35;
    private static final double MAXIMUM_STAIR_TREAD_RUN = 1.65;
    private static final int PHYSICS_MASK = VehicleCollisionRole.PHYSICS.mask();

    public static Vec3 resolve(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            Vec3 requestedMovement
    ) {
        if (requestedMovement.lengthSqr() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return requestedMovement;
        }

        List<AABB> obstacles = collectWorldObstacles(
                vehicle,
                model,
                requestedMovement
        );
        Vec3 resolved = resolveAgainstAabbs(
                model,
                vehicle.position(),
                vehicle.getYRot(),
                requestedMovement,
                vehicle.maxUpStep(),
                vehicle.onGround(),
                obstacles
        );
        List<RampShape> ramps = collectRampObstacles(vehicle, model, requestedMovement);
        if (!ramps.isEmpty()) {
            resolved = resolveAgainstRamps(model, vehicle.position(), vehicle.getYRot(),
                    resolved, obstacles, ramps, vehicle.maxUpStep(), vehicle.onGround());
        }
        return resolved;
    }

    /**
     * Computes the vanilla-style up-then-horizontal correction required for a
     * Bullet-driven vehicle to cross a legal step. Bullet owns the actual
     * rigid body and therefore never calls Minecraft's entity collision step for its
     * chassis transform; without forwarding this correction,
     * {@code maximum_step_height} has no effect while driving.
     */
    public static Maybe<BulletStepPlan> findBulletStepPlan(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            Vec3 horizontalProbe
    ) {
        Vec3 requested = new Vec3(horizontalProbe.x, 0.0, horizontalProbe.z);
        if (!vehicle.onGround()
                || vehicle.maxUpStep() <= 0.0F
                || requested.horizontalDistanceSqr() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return Maybe.none();
        }
        List<AABB> obstacles = collectWorldObstacles(vehicle, model, requested);
        Vec3 clipped = resolveAxes(
                model,
                vehicle.position(),
                vehicle.getYRot(),
                requested,
                obstacles
        );
        boolean blocked = clipped.horizontalDistanceSqr()
                + MOVEMENT_EPSILON < requested.horizontalDistanceSqr();
        if (!blocked) {
            return Maybe.none();
        }
        Vec3 resolved = resolveAgainstAabbs(
                model,
                vehicle.position(),
                vehicle.getYRot(),
                requested,
                vehicle.maxUpStep(),
                true,
                obstacles
        );
        boolean stepped = resolved.y > STEP_EPSILON;
        boolean retainedDirection = resolved.x * requested.x + resolved.z * requested.z
                > MOVEMENT_EPSILON;
        if (!stepped || !retainedDirection) {
            return Maybe.none();
        }
        double targetSurfaceY = model.physicsWorldBounds(
                vehicle.position(),
                vehicle.getYRot()
        ).minY + resolved.y;
        Vec3 stepDirection = requested.normalize();
        List<AABB> sameHeightSurfaces = obstacles.stream()
                .filter(obstacle -> Math.abs(obstacle.maxY - targetSurfaceY) <= STEP_EPSILON)
                .toList();
        if (sameHeightSurfaces.isEmpty()) {
            return Maybe.none();
        }
        // Keep the complete same-height platform inside the swept broad phase.
        // Selecting only the mathematically first projected block fails for an
        // oblique approach: the left and right tires then see different entry
        // blocks and only one side receives a climb constraint.
        List<AABB> targetSurfaces = sameHeightSurfaces;
        double baseSurfaceY = targetSurfaces.stream()
                .mapToDouble(surface -> surface.minY)
                .max()
                .orElse(targetSurfaceY - resolved.y);
        return Maybe.some(new BulletStepPlan(
                resolved,
                clipped.horizontalDistance(),
                baseSurfaceY,
                targetSurfaceY,
                stepDirection,
                targetSurfaces
        ));
    }

    /**
     * Expands the first legal step into consecutive higher tiers found in the
     * same swept broad phase. This lets physics register the complete virtual
     * slope before the chassis reaches the next vertical riser.
     */
    public static List<BulletStepPlan> findBulletStairPlans(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            Vec3 horizontalProbe
    ) {
        Maybe<BulletStepPlan> firstPlan = findBulletStepPlan(
                vehicle,
                model,
                horizontalProbe
        );
        if (firstPlan.isEmpty()) {
            return List.of();
        }
        BulletStepPlan first = firstPlan.get();
        List<BulletStepPlan> result = new ArrayList<>();
        result.add(first);
        AABB stairScanBounds = model.physicsWorldBounds(
                        vehicle.position(),
                        vehicle.getYRot()
                )
                .expandTowards(horizontalProbe.x, 0.0, horizontalProbe.z)
                .expandTowards(
                        0.0,
                        vehicle.maxUpStep() * MAXIMUM_STAIR_TIERS,
                        0.0
                )
                .inflate(STEP_EPSILON);
        List<AABB> obstacles = collectWorldObstacles(vehicle, stairScanBounds);
        Vec3 stairDirection = first.direction();
        BulletStepPlan current = first;
        double currentFace = current.targetSurfaces().stream()
                .mapToDouble(surface -> minimumProjection(
                        surface,
                        stairDirection.x,
                        stairDirection.z
                ))
                .min()
                .orElse(Double.NaN);
        for (int tier = 1; tier < MAXIMUM_STAIR_TIERS; tier++) {
            final BulletStepPlan preceding = current;
            final double precedingFace = currentFace;
            AABB nextSurface = null;
            double nextFace = Double.POSITIVE_INFINITY;
            for (AABB surface : obstacles) {
                if (surface.maxY <= preceding.targetSurfaceY() + STEP_EPSILON
                        || surface.maxY > preceding.targetSurfaceY() + vehicle.maxUpStep() + STEP_EPSILON) {
                    continue;
                }
                double face = minimumProjection(surface, preceding.direction().x, preceding.direction().z);
                double run = face - precedingFace;
                if (run >= MINIMUM_STAIR_TREAD_RUN - STEP_EPSILON
                        && run <= MAXIMUM_STAIR_TREAD_RUN + STEP_EPSILON
                        && (nextSurface == null || Double.compare(face, nextFace) < 0)) {
                    nextSurface = surface;
                    nextFace = face;
                }
            }
            if (nextSurface == null) break;
            double nextTargetY = nextSurface.maxY;
            final double selectedFace = nextFace;
            List<AABB> tierSurfaces = obstacles.stream()
                    .filter(surface -> Math.abs(surface.maxY - nextTargetY)
                            <= STEP_EPSILON)
                    .filter(surface -> Math.abs(minimumProjection(
                            surface,
                            preceding.direction().x,
                            preceding.direction().z
                    ) - selectedFace) <= 0.25)
                    .toList();
            if (tierSurfaces.isEmpty()) {
                break;
            }
            double run = nextFace - precedingFace;
            current = new BulletStepPlan(
                    new Vec3(
                            preceding.direction().x * run,
                            nextTargetY - preceding.targetSurfaceY(),
                            preceding.direction().z * run
                    ),
                    preceding.approachDistance() + run,
                    preceding.targetSurfaceY(),
                    nextTargetY,
                    preceding.direction(),
                    tierSurfaces
            );
            result.add(current);
            currentFace = nextFace;
        }
        return List.copyOf(result);
    }

    public record BulletStepPlan(
            Vec3 adjustment,
            double approachDistance,
            double baseSurfaceY,
            double targetSurfaceY,
            Vec3 direction,
            List<AABB> targetSurfaces
    ) {
        public BulletStepPlan {
            Objects.requireNonNull(adjustment, "adjustment");
            Objects.requireNonNull(direction, "direction");
            approachDistance = Math.max(0.0, approachDistance);
            if (!Double.isFinite(baseSurfaceY) || !Double.isFinite(targetSurfaceY)) {
                throw new IllegalArgumentException("step surface heights must be finite");
            }
            if (direction.horizontalDistanceSqr() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
                throw new IllegalArgumentException("step direction must be horizontal and non-zero");
            }
            if (targetSurfaces.isEmpty()) {
                throw new IllegalArgumentException("step plan requires target surfaces");
            }
        }
    }

    private static double minimumProjection(
            AABB box,
            double axisX,
            double axisZ
    ) {
        return (axisX >= 0.0 ? box.minX : box.maxX) * axisX
                + (axisZ >= 0.0 ? box.minZ : box.maxZ) * axisZ;
    }

    /**
     * Clips a steering rotation before the front or rear of a long vehicle can
     * rotate through a wall. Translation still uses continuous SAT; angular
     * motion is sampled at at most two-degree intervals and the first blocked
     * interval is refined with a binary search.
     */
    public static float resolveYaw(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            float requestedYawDegrees
    ) {
        float currentYaw = vehicle.getYRot();
        float yawDelta = Mth.wrapDegrees(requestedYawDegrees - currentYaw);
        if (Math.abs(yawDelta) <= MOVEMENT_EPSILON) {
            return requestedYawDegrees;
        }
        AABB broadPhase = model.physicsRotationBounds(vehicle.position())
                .inflate(STEP_EPSILON);
        return resolveYawAgainstAabbs(
                model,
                vehicle.position(),
                currentYaw,
                requestedYawDegrees,
                collectWorldObstacles(vehicle, broadPhase)
        );
    }

    static Vec3 resolveAgainstAabbs(
            VehicleCollisionModel model,
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            Vec3 requestedMovement,
            float maximumStepHeight,
            boolean onGround,
            List<AABB> obstacles
    ) {
        if (obstacles.isEmpty()
                || requestedMovement.lengthSqr() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return requestedMovement;
        }

        Vec3 clipped = resolveAxes(
                model,
                vehiclePosition,
                vehicleYawDegrees,
                requestedMovement,
                obstacles
        );
        boolean horizontalCollision = !approximatelyEqual(
                requestedMovement.x,
                clipped.x
        ) || !approximatelyEqual(requestedMovement.z, clipped.z);
        boolean verticalCollisionBelow = requestedMovement.y < 0.0
                && !approximatelyEqual(requestedMovement.y, clipped.y);
        if (maximumStepHeight <= 0.0F
                || !(onGround || verticalCollisionBelow)
                || !horizontalCollision) {
            return clipped;
        }

        Vec3 stepped = attemptStep(
                model,
                vehiclePosition,
                vehicleYawDegrees,
                requestedMovement,
                clipped,
                maximumStepHeight,
                obstacles
        );
        return stepped.horizontalDistanceSqr() > clipped.horizontalDistanceSqr()
                + MOVEMENT_EPSILON
                ? stepped
                : clipped;
    }

    static float resolveYawAgainstAabbs(
            VehicleCollisionModel model,
            Vec3 vehiclePosition,
            float currentYawDegrees,
            float requestedYawDegrees,
            List<AABB> obstacles
    ) {
        float yawDelta = Mth.wrapDegrees(requestedYawDegrees - currentYawDegrees);
        if (obstacles.isEmpty() || Math.abs(yawDelta) <= MOVEMENT_EPSILON) {
            return requestedYawDegrees;
        }

        int samples = Math.max(
                1,
                (int) Math.ceil(Math.abs(yawDelta) / MAXIMUM_YAW_SAMPLE_DEGREES)
        );
        double previousFraction = 0.0;
        for (int sample = 1; sample <= samples; sample++) {
            double fraction = (double) sample / samples;
            float sampledYaw = currentYawDegrees + (float) (yawDelta * fraction);
            if (!intersectsPhysics(
                    model,
                    vehiclePosition,
                    sampledYaw,
                    obstacles
            )) {
                previousFraction = fraction;
                continue;
            }

            double blockedFraction = fraction;
            for (int iteration = 0; iteration < YAW_BINARY_SEARCH_STEPS; iteration++) {
                double middle = (previousFraction + blockedFraction) * 0.5;
                float middleYaw = currentYawDegrees + (float) (yawDelta * middle);
                if (intersectsPhysics(model, vehiclePosition, middleYaw, obstacles)) {
                    blockedFraction = middle;
                } else {
                    previousFraction = middle;
                }
            }
            return currentYawDegrees + (float) (yawDelta * previousFraction);
        }
        return requestedYawDegrees;
    }

    private static Vec3 resolveAxes(
            VehicleCollisionModel model,
            Vec3 initialPosition,
            float yaw,
            Vec3 movement,
            List<AABB> obstacles
    ) {
        Vec3 position = initialPosition;
        double y = clipAxis(
                model,
                position,
                yaw,
                new Vec3(0.0, movement.y, 0.0),
                obstacles
        ).y;
        position = position.add(0.0, y, 0.0);

        boolean resolveZFirst = Math.abs(movement.x) < Math.abs(movement.z);
        double x;
        double z;
        if (resolveZFirst) {
            z = clipAxis(
                    model,
                    position,
                    yaw,
                    new Vec3(0.0, 0.0, movement.z),
                    obstacles
            ).z;
            position = position.add(0.0, 0.0, z);
            x = clipAxis(
                    model,
                    position,
                    yaw,
                    new Vec3(movement.x, 0.0, 0.0),
                    obstacles
            ).x;
        } else {
            x = clipAxis(
                    model,
                    position,
                    yaw,
                    new Vec3(movement.x, 0.0, 0.0),
                    obstacles
            ).x;
            position = position.add(x, 0.0, 0.0);
            z = clipAxis(
                    model,
                    position,
                    yaw,
                    new Vec3(0.0, 0.0, movement.z),
                    obstacles
            ).z;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 attemptStep(
            VehicleCollisionModel model,
            Vec3 initialPosition,
            float yaw,
            Vec3 requested,
            Vec3 clipped,
            float maximumStepHeight,
            List<AABB> obstacles
    ) {
        Vec3 groundedPosition = initialPosition.add(0.0, clipped.y, 0.0);
        AABB groundedBounds = model.physicsWorldBounds(groundedPosition, yaw);
        List<Double> candidateHeights = new ArrayList<>();
        for (AABB obstacle : obstacles) {
            double height = obstacle.maxY - groundedBounds.minY;
            if (height > STEP_EPSILON && height <= maximumStepHeight + STEP_EPSILON) {
                addDistinct(candidateHeights, Math.min(height, maximumStepHeight));
            }
        }
        candidateHeights.sort(Comparator.naturalOrder());

        Vec3 best = clipped;
        for (double candidateHeight : candidateHeights) {
            Vec3 upward = clipAxis(
                    model,
                    groundedPosition,
                    yaw,
                    new Vec3(0.0, candidateHeight, 0.0),
                    obstacles
            );
            if (upward.y + STEP_EPSILON < candidateHeight) {
                continue;
            }
            Vec3 raisedPosition = groundedPosition.add(upward);
            Vec3 horizontal = resolveAxes(
                    model,
                    raisedPosition,
                    yaw,
                    new Vec3(requested.x, 0.0, requested.z),
                    obstacles
            );
            Vec3 candidate = new Vec3(
                    horizontal.x,
                    clipped.y + upward.y,
                    horizontal.z
            );
            if (candidate.horizontalDistanceSqr() > best.horizontalDistanceSqr()
                    + MOVEMENT_EPSILON) {
                best = candidate;
            }
        }
        return best;
    }

    private static Vec3 clipAxis(
            VehicleCollisionModel model,
            Vec3 position,
            float yaw,
            Vec3 movement,
            List<AABB> obstacles
    ) {
        if (movement.lengthSqr() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return movement;
        }
        double earliestFraction = 1.0;
        boolean collisionFound = false;
        for (AABB obstacle : obstacles) {
            double fraction = model.sweepFractionAgainstWorldAabb(
                    position,
                    yaw,
                    obstacle,
                    movement,
                    PHYSICS_MASK,
                    true
            );
            if (Double.isFinite(fraction)) {
                collisionFound = true;
                earliestFraction = Math.min(earliestFraction, fraction);
                if (earliestFraction <= 0.0) {
                    return Vec3.ZERO;
                }
            }
        }
        if (!collisionFound) {
            return movement;
        }
        double movementLength = movement.length();
        double safeFraction = Math.max(
                0.0,
                earliestFraction - COLLISION_SKIN / movementLength
        );
        return movement.scale(safeFraction);
    }

    private static List<AABB> collectWorldObstacles(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            Vec3 requestedMovement
    ) {
        AABB broadPhase = model.physicsWorldBounds(vehicle.position(), vehicle.getYRot())
                .expandTowards(requestedMovement)
                .expandTowards(0.0, vehicle.maxUpStep(), 0.0)
                .inflate(STEP_EPSILON);
        return collectWorldObstacles(vehicle, broadPhase);
    }

    private static List<RampShape> collectRampObstacles(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model,
            Vec3 requestedMovement
    ) {
        AABB broadPhase = model.physicsWorldBounds(vehicle.position(), vehicle.getYRot())
                .expandTowards(requestedMovement)
                .expandTowards(0.0, vehicle.maxUpStep(), 0.0)
                .inflate(STEP_EPSILON);
        List<RampShape> ramps = new ArrayList<>();
        for (VoxelShape shape : vehicle.level().getBlockCollisions(vehicle, broadPhase)) {
            if (shape instanceof RampShape ramp) {
                ramps.add(ramp);
            }
        }
        return ramps;
    }

    private static Vec3 resolveAgainstRamps(
            VehicleCollisionModel model,
            Vec3 vehiclePosition,
            float yaw,
            Vec3 movement,
            List<AABB> ordinaryObstacles,
            List<RampShape> ramps,
            float maxStepHeight,
            boolean onGround
    ) {
        Vec3 traveled = Vec3.ZERO;
        Vec3 remaining = movement;
        for (int contact = 0; contact < 6 && remaining.lengthSqr() > MOVEMENT_EPSILON * MOVEMENT_EPSILON;
             contact++) {
            RampShape.SweepHit earliest = null;
            for (VehicleCollisionCube cube : model.cubes()) {
                if (!cube.participatesIn(PHYSICS_MASK, true)) {
                    continue;
                }
                OrientedBoundingBox box = cube.box().toWorld(vehiclePosition.add(traveled), yaw);
                for (RampShape ramp : ramps) {
                    RampShape.SweepHit hit = ramp.sweep(box, remaining);
                    if (hit != null && (earliest == null || hit.time() < earliest.time())) {
                        earliest = hit;
                    }
                }
            }
            if (earliest == null) {
                traveled = traveled.add(remaining);
                break;
            }
            double safeFraction = Math.max(0.0,
                    earliest.time() - COLLISION_SKIN / Math.max(remaining.length(), MOVEMENT_EPSILON));
            traveled = traveled.add(remaining.scale(safeFraction));
            remaining = remaining.scale(1.0 - safeFraction);
            double intoSlope = remaining.dot(earliest.normal());
            if (intoSlope < 0.0) {
                remaining = remaining.subtract(earliest.normal().scale(intoSlope));
            }
        }
        // Projecting uphill can create upward motion; preserve ordinary ceilings and walls.
        return resolveAgainstAabbs(model, vehiclePosition, yaw, traveled, maxStepHeight,
                onGround, ordinaryObstacles);
    }

    private static List<AABB> collectWorldObstacles(
            AbstractVehicleEntity vehicle,
            AABB broadPhase
    ) {
        Level level = vehicle.level();
        List<AABB> obstacles = new ArrayList<>();
        for (VoxelShape shape : level.getEntityCollisions(vehicle, broadPhase)) {
            addShapeBoxes(shape, obstacles);
        }
        for (VoxelShape shape : level.getBlockCollisions(vehicle, broadPhase)) {
            addShapeBoxes(shape, obstacles);
        }

        WorldBorder worldBorder = level.getWorldBorder();
        if (worldBorder.isInsideCloseToBorder(vehicle, broadPhase)) {
            addShapeBoxes(worldBorder.getCollisionShape(), obstacles);
        }
        return obstacles;
    }

    private static boolean intersectsPhysics(
            VehicleCollisionModel model,
            Vec3 vehiclePosition,
            float yaw,
            List<AABB> obstacles
    ) {
        for (AABB obstacle : obstacles) {
            AABB contactSafeObstacle = obstacle.deflate(COLLISION_SKIN);
            if (contactSafeObstacle.getXsize() <= 0.0
                    || contactSafeObstacle.getYsize() <= 0.0
                    || contactSafeObstacle.getZsize() <= 0.0) {
                continue;
            }
            if (model.intersectsWorldAabb(
                    vehiclePosition,
                    yaw,
                    contactSafeObstacle,
                    PHYSICS_MASK,
                    true
            )) {
                return true;
            }
        }
        return false;
    }

    private static void addShapeBoxes(VoxelShape shape, List<AABB> output) {
        if (!shape.isEmpty() && !(shape instanceof RampShape)) {
            output.addAll(shape.toAabbs());
        }
    }

    private static void addDistinct(List<Double> values, double candidate) {
        for (double value : values) {
            if (Math.abs(value - candidate) <= STEP_EPSILON) {
                return;
            }
        }
        values.add(candidate);
    }

    private static boolean approximatelyEqual(double first, double second) {
        return Math.abs(first - second) <= MOVEMENT_EPSILON;
    }
}
