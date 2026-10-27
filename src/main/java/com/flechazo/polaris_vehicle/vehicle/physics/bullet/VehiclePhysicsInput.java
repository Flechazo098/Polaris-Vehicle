package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePerformance;

import java.util.List;
import java.util.Objects;

/**
 * Immutable main-thread input consumed by the Spark physics thread.
 */
public record VehiclePhysicsInput(
        double throttle,
        double steering,
        boolean brake,
        boolean attended,
        VehicleSteeringCurve steeringCurve,
        VehiclePerformance performance,
        List<Boolean> installedWheels,
        List<Double> wheelGripCoefficients,
        List<Double> wheelRadii,
        VehicleSteeringResponse steeringResponse
) {

    public VehiclePhysicsInput(double throttle, double steering, boolean brake, boolean attended,
                               VehicleSteeringCurve steeringCurve, VehiclePerformance performance,
                               List<Boolean> installedWheels, List<Double> wheelGripCoefficients, List<Double> wheelRadii) {
        this(throttle, steering, brake, attended, steeringCurve, performance,
                installedWheels, wheelGripCoefficients, wheelRadii, VehicleSteeringResponse.DEFAULT);
    }

    public VehiclePhysicsInput {
        throttle = Math.clamp(throttle, -1.0, 1.0);
        steering = Math.clamp(steering, -1.0, 1.0);
        Objects.requireNonNull(steeringCurve, "steeringCurve");
        Objects.requireNonNull(steeringResponse, "steeringResponse");
        Objects.requireNonNull(performance, "performance");
        if (installedWheels.size() < 3 || installedWheels.size() > 4) {
            throw new IllegalArgumentException("Three or four wheel installation flags are required");
        }
        if (wheelGripCoefficients.size() != installedWheels.size()) {
            throw new IllegalArgumentException("Wheel grip coefficients must align with wheel flags");
        }
        if (wheelRadii.size() != installedWheels.size()) {
            throw new IllegalArgumentException("Wheel radii must align with wheel flags");
        }
    }

    public static VehiclePhysicsInput idle(VehiclePerformance performance) {
        return new VehiclePhysicsInput(
                0.0, 0.0, true, false, VehicleSteeringCurve.DEFAULT,
                performance,
                List.of(false, false, false, false),
                List.of(1.0, 1.0, 1.0, 1.0),
                List.of(0.5, 0.5, 0.5, 0.5)
        );
    }
}
