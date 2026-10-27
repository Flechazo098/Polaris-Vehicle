package com.flechazo.polaris_vehicle.vehicle.physics;

public final class VehiclePhysicsStep {

    public static float seconds(int substeps) {
        if (substeps < 1) throw new IllegalArgumentException("At least one physics substep is required");
        return .05F / substeps;
    }

    public static long sleepMillis(long targetNanos, double smoothedActiveNanos, int substeps) {
        if (substeps < 1) throw new IllegalArgumentException("At least one physics substep is required");
        return (long) (Math.max(0.0, targetNanos - smoothedActiveNanos) / substeps / 1_000_000.0);
    }

    public static long activeNanos(long elapsedNanos, long sleptNanos) {
        return Math.max(0L, elapsedNanos - Math.max(0L, sleptNanos));
    }
}
