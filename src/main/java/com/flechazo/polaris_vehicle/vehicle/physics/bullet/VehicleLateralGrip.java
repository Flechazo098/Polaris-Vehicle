package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Passive, load-limited lateral tire response at a supported suspension mount.
 */
final class VehicleLateralGrip {
    private VehicleLateralGrip() {
    }

    static Vector3f force(Quaternion chassisRotation, double wheelAngle, Vector3f groundNormal,
                          Vector3f pointVelocity, double supportedMass, double grip,
                          double response, double dt) {
        Vector3f axle = VehicleContactMath.rotate(chassisRotation,
                new Vector3f((float) Math.cos(wheelAngle), 0, (float) -Math.sin(wheelAngle)));
        axle = axle.subtract(groundNormal.mult(axle.dot(groundNormal)));
        if (axle.lengthSquared() < 1E-6 || !(supportedMass > 0) || !(dt > 0)) return new Vector3f();
        axle = axle.normalize();
        double slip = pointVelocity.dot(axle);
        double desiredForce = -slip * supportedMass * Math.min(response, 0.5 / dt);
        double loadLimit = supportedMass * 9.81 * Math.max(0, grip) * Math.max(0, groundNormal.y);
        return axle.mult((float) Math.clamp(desiredForce, -loadLimit, loadLimit));
    }
}
