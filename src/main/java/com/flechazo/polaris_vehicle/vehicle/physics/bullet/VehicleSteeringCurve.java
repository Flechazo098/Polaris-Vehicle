package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

/**
 * Configurable speed-sensitive steering attenuation published to Bullet.
 */
public record VehicleSteeringCurve(
        double limitStartRatio,
        double minimumScale
) {
    public static final VehicleSteeringCurve DEFAULT = new VehicleSteeringCurve(0.35, 0.25);

    public VehicleSteeringCurve {
        if (!Double.isFinite(limitStartRatio)
                || !Double.isFinite(minimumScale)
                || limitStartRatio < 0.0
                || limitStartRatio > 1.0
                || minimumScale < 0.0
                || minimumScale > 1.0) {
            throw new IllegalArgumentException("Steering curve values must be finite and between 0 and 1");
        }
    }

    public double scale(double speed, double maximumSpeed) {
        return VehicleDynamics.steeringScale(
                speed,
                // Kept in the public signature for compatibility. Steering
                // uses a fixed 30 m/s reference, independently of tire wear,
                // engine power and changing fuel load.
                maximumSpeed,
                limitStartRatio,
                minimumScale
        );
    }
}
