package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

/**
 * Driver preference; the server still decides the available road-wheel angle.
 */
public record VehicleSteeringResponse(boolean instant, double lockSeconds, double returnSeconds) {
    public static final VehicleSteeringResponse DEFAULT = new VehicleSteeringResponse(false, 0.30, 0.20);
    public VehicleSteeringResponse {
        if (!Double.isFinite(lockSeconds) || lockSeconds < 0.05 || lockSeconds > 40.0
                || !Double.isFinite(returnSeconds) || returnSeconds < 0.10 || returnSeconds > 3.0) {
            throw new IllegalArgumentException("Invalid steering response time");
        }
    }
}
