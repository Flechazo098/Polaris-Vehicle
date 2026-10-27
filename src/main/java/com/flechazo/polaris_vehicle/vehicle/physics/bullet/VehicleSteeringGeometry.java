package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

/**
 * Ackermann steering and differential road-speed targets for real wheel motors.
 */
public final class VehicleSteeringGeometry {
    private static final double CORNERING_GRIP_SHARE = 0.66;
    private final VehiclePhysicsProfile profile;
    private final double rearZ;
    private final double rearX;
    private final double wheelbase;

    public VehicleSteeringGeometry(VehiclePhysicsProfile profile) {
        this.profile = profile;
        rearZ = profile.wheels().stream().filter(w -> !w.steerable())
                .mapToDouble(w -> w.wheelCenter().z).average().orElseThrow();
        rearX = profile.wheels().stream().filter(w -> !w.steerable())
                .mapToDouble(w -> w.wheelCenter().x).average().orElse(0);
        double frontZ = profile.wheels().stream().filter(VehicleWheelMount::steerable)
                .mapToDouble(w -> w.wheelCenter().z).average().orElseThrow();
        wheelbase = Math.max(0.1, Math.abs(frontZ - rearZ));
    }

    public WheelTarget target(int index, double centralAngle) {
        var wheel = profile.wheels().get(index);
        double curvature = Math.tan(centralAngle) / wheelbase;
        double lateralFactor = 1 - curvature * (wheel.wheelCenter().x - rearX);
        double longitudinalFactor = curvature * (wheel.wheelCenter().z - rearZ);
        double angle = wheel.steerable() ? Math.atan2(longitudinalFactor, lateralFactor) : 0;
        return new WheelTarget(angle, Math.hypot(lateralFactor, longitudinalFactor));
    }

    /**
     * Smooth saturation in curvature, keeping a_y = v² * tan(delta) / wheelbase.
     */
    double assistedAngle(double requestedAngle, double speed, double grip) {
        double desired = Math.tan(requestedAngle) / wheelbase;
        double limit = Math.max(0.1, grip) * 9.81 * CORNERING_GRIP_SHARE / Math.max(0.1, speed * speed);
        double ratio = desired / limit;
        double curvature = desired / Math.pow(1 + Math.pow(ratio, 4), 0.25);
        return Math.atan(curvature * wheelbase);
    }

    double gripLimitedAngle(double speed, double grip) {
        // Leave load capacity for acceleration and the finite tire response;
        // asking for nearly all static grip produces sustained corner slip.
        double lateralAcceleration = Math.max(0.1, grip) * 9.81 * CORNERING_GRIP_SHARE;
        return Math.atan(lateralAcceleration * wheelbase / Math.max(0.1, speed * speed));
    }

    public record WheelTarget(double steeringAngle, double roadSpeedScale) {
    }
}
