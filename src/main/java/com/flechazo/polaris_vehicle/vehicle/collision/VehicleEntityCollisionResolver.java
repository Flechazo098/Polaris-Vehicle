package com.flechazo.polaris_vehicle.vehicle.collision;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleMaintenanceState;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VehicleEntityCollisionResolver {
    private static final double MOVEMENT_EPSILON = 1.0E-7;
    private static final double COLLISION_SKIN = 2.0E-6;
    private static final double QUERY_MARGIN = 8.0;
    private static final int PHYSICS_MASK = VehicleCollisionRole.PHYSICS.mask();

    public static Vec3 resolve(Entity entity, Vec3 vanillaResolvedMovement) {
        if (!shouldResolve(entity)
                || vanillaResolvedMovement.lengthSqr()
                <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return vanillaResolvedMovement;
        }

        AABB entityBounds = entity.getBoundingBox();
        AABB queryBounds = entityBounds
                .expandTowards(vanillaResolvedMovement)
                .inflate(QUERY_MARGIN);
        VehicleStructureModelManager manager = VehicleStructureModelManager.forLevel(
                entity.level()
        );
        List<CollisionTarget> targets = new ArrayList<>();
        for (AbstractVehicleEntity vehicle : entity.level().getEntitiesOfClass(
                AbstractVehicleEntity.class,
                queryBounds,
                candidate -> canCollide(entity, candidate)
        )) {
            manager.find(vehicle.vehicleTypeId()).fold(
                    () -> false,
                    model -> {
                        AABB sweptEntityBounds = entityBounds
                                .expandTowards(vanillaResolvedMovement)
                                .inflate(COLLISION_SKIN);
                        if (model.physicsWorldBounds(
                                vehicle.position(),
                                vehicle.vehicleBodyRotation()
                        ).intersects(sweptEntityBounds)) {
                            targets.add(new CollisionTarget(
                                    model,
                                    vehicle.position(),
                                    vehicle.vehicleBodyRotation()
                            ));
                        }
                        return true;
                    }
            );
        }
        return resolveAgainstTargets(entityBounds, vanillaResolvedMovement, targets);
    }

    static Vec3 resolveAgainstTargets(
            AABB initialBounds,
            Vec3 requestedMovement,
            List<CollisionTarget> targets
    ) {
        if (targets.isEmpty()
                || requestedMovement.lengthSqr()
                <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return requestedMovement;
        }

        AABB bounds = initialBounds;
        double y = clipAxis(
                bounds,
                new Vec3(0.0, requestedMovement.y, 0.0),
                targets
        ).y;
        bounds = bounds.move(0.0, y, 0.0);

        boolean resolveZFirst = Math.abs(requestedMovement.x)
                < Math.abs(requestedMovement.z);
        double x;
        double z;
        if (resolveZFirst) {
            z = clipAxis(
                    bounds,
                    new Vec3(0.0, 0.0, requestedMovement.z),
                    targets
            ).z;
            bounds = bounds.move(0.0, 0.0, z);
            x = clipAxis(
                    bounds,
                    new Vec3(requestedMovement.x, 0.0, 0.0),
                    targets
            ).x;
        } else {
            x = clipAxis(
                    bounds,
                    new Vec3(requestedMovement.x, 0.0, 0.0),
                    targets
            ).x;
            bounds = bounds.move(x, 0.0, 0.0);
            z = clipAxis(
                    bounds,
                    new Vec3(0.0, 0.0, requestedMovement.z),
                    targets
            ).z;
        }
        return new Vec3(x, y, z);
    }

    private static Vec3 clipAxis(
            AABB entityBounds,
            Vec3 entityMovement,
            List<CollisionTarget> targets
    ) {
        if (entityMovement.lengthSqr()
                <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return entityMovement;
        }
        double earliestFraction = 1.0;
        boolean collisionFound = false;
        // Moving the entity by M relative to a stationary vehicle is the same
        // continuous-SAT problem as moving the vehicle by -M relative to the
        // entity's initial AABB.
        Vec3 inverseMovement = entityMovement.scale(-1.0);
        for (CollisionTarget target : targets) {
            double fraction = target.model().sweepFractionAgainstWorldAabb(
                    target.position(),
                    target.rotation(),
                    entityBounds,
                    inverseMovement,
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
            return entityMovement;
        }
        double safeFraction = Math.max(
                0.0,
                earliestFraction - COLLISION_SKIN / entityMovement.length()
        );
        return entityMovement.scale(safeFraction);
    }

    private static boolean shouldResolve(Entity entity) {
        return !(entity instanceof AbstractVehicleEntity)
                && !entity.noPhysics
                && !entity.isSpectator()
                && !entity.isRemoved();
    }

    private static boolean canCollide(
            Entity entity,
            AbstractVehicleEntity vehicle
    ) {
        VehicleMaintenanceState state = vehicle.maintenanceState();
        return !vehicle.isRemoved()
                && state != VehicleMaintenanceState.CARRIED
                && state != VehicleMaintenanceState.DISASSEMBLING
                && state != VehicleMaintenanceState.REMOVED
                && entity.getRootVehicle() != vehicle
                && !vehicle.hasIndirectPassenger(entity)
                && !entity.isPassengerOfSameVehicle(vehicle);
    }

    record CollisionTarget(
            VehicleCollisionModel model,
            Vec3 position,
            Quaternionf rotation
    ) {
        CollisionTarget {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(position, "position");
            rotation = new Quaternionf(rotation);
        }

        @Override
        public Quaternionf rotation() {
            return new Quaternionf(rotation);
        }
    }
}
