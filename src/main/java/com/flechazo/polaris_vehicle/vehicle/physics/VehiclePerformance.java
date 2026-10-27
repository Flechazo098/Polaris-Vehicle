package com.flechazo.polaris_vehicle.vehicle.physics;

public record VehiclePerformance(
        double maxSpeedMetersPerSecond,
        double accelerationMetersPerSecondSquared,
        double reverseSpeedMetersPerSecond,
        double brakingMetersPerSecondSquared,
        boolean canDrive,
        double accelerationRampSeconds
) {

    public VehiclePerformance(double maxSpeedMetersPerSecond, double accelerationMetersPerSecondSquared,
                              double reverseSpeedMetersPerSecond, double brakingMetersPerSecondSquared, boolean canDrive) {
        this(maxSpeedMetersPerSecond, accelerationMetersPerSecondSquared, reverseSpeedMetersPerSecond,
                brakingMetersPerSecondSquared, canDrive, 3.0);
    }

    public VehiclePerformance withMaxSpeedBonus(double bonusMetersPerSecond) {
        if (bonusMetersPerSecond <= 0.0) {
            return this;
        }
        double newMaximumSpeed = maxSpeedMetersPerSecond + bonusMetersPerSecond;
        return new VehiclePerformance(
                newMaximumSpeed,
                accelerationMetersPerSecondSquared,
                Math.clamp(newMaximumSpeed * 0.3, 2.0, 5.0),
                brakingMetersPerSecondSquared,
                canDrive,
                accelerationRampSeconds
        );
    }
}
