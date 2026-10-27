package com.flechazo.polaris_vehicle.vehicle.collision;

import java.util.Locale;

/**
 * Initial semantic role inference for the supplied models. The rules operate
 * on bone ancestry, so every cube remains addressable even when it is excluded
 * from rigid-body contact. A future data-pack sidecar can override these roles
 * without changing the collision engine.
 */
final class VehicleCollisionRoles {
    static VehicleCollisionRole classify(
            String boneName,
            VehicleCollisionRole inheritedRole
    ) {
        String normalized = boneName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("interaction_wheel_")) return VehicleCollisionRole.SENSOR;

        return VehicleCollisionRole.PHYSICS;
    }
}
