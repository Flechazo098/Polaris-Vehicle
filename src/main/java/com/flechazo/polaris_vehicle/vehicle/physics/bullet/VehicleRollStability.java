package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Bounded suspension roll assistance around the actual supporting surface, never a pose reset.
 */
final class VehicleRollStability {
    private VehicleRollStability() {
    }

    static Vector3f torque(Quaternion rotation, Vector3f angularVelocity, Vector3f inverseInertia,
                           Vector3f supportNormal, int supports, double dt) {
        if (supports == 0 || supportNormal.lengthSquared() < .01 || dt <= 0) return new Vector3f();
        Vector3f up = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Y);
        Vector3f right = VehicleContactMath.rotate(rotation, Vector3f.UNIT_X);
        Vector3f forward = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Z);
        // Overturned cars retain the driver's recovery controls. Airborne
        // cars retain their momentum; a wall normal is not road support.
        if (up.y < .5F || supportNormal.y < .5F || inverseInertia.z <= 1E-9) return new Vector3f();
        Vector3f normal = supportNormal.normalize();
        double error = Math.atan2(right.dot(normal), up.dot(normal));
        double rollRate = angularVelocity.dot(forward);
        double alpha = Math.clamp(-error * 20 - rollRate * Math.min(7, .5 / dt), -8, 8);
        // About the chassis longitudinal axis only: steering yaw and ramp
        // pitch are unaffected, including on banked roads.
        return forward.mult((float) (alpha / inverseInertia.z));
    }
}
