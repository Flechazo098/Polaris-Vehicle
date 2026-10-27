package com.flechazo.polaris_vehicle.vehicle.collision;

/**
 * Semantic use of a complete Bedrock model cube. Geometry is never discarded;
 * callers select the roles appropriate for a physics, hit or sensor query.
 */
public enum VehicleCollisionRole {
    PHYSICS,
    HIT_ONLY,
    DECORATION,
    SENSOR;

    public static final int ALL_MASK = PHYSICS.mask()
            | HIT_ONLY.mask()
            | DECORATION.mask()
            | SENSOR.mask();

    public int mask() {
        return 1 << ordinal();
    }

    public boolean isIncludedIn(int roleMask) {
        return (roleMask & mask()) != 0;
    }

    public static int maskOf(VehicleCollisionRole first, VehicleCollisionRole... rest) {
        int mask = first.mask();
        for (VehicleCollisionRole role : rest) {
            mask |= role.mask();
        }
        return mask;
    }
}
