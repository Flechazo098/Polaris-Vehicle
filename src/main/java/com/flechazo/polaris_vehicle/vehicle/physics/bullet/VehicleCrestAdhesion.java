package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Limited gameplay assistance at the end of a supported climb, using forces only.
 */
final class VehicleCrestAdhesion {
    private double groundAge = Double.POSITIVE_INFINITY;
    private double climbAge = Double.POSITIVE_INFINITY;
    private double approachSpeed;
    private double assistScale;

    Vector3f force(double dt, boolean attended, boolean grounded, Quaternion rotation,
                   Vector3f velocity, double groundSlope, double mass) {
        Vector3f up = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Y);
        Vector3f forward = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Z);
        groundAge = grounded ? 0 : groundAge + dt;
        boolean climbing = grounded && Math.abs(forward.y) > 0.12
                && Math.abs(velocity.y) > 0.4;
        double speed = Math.hypot(velocity.x, velocity.z);
        if (climbAge > 0.65 && !climbing) approachSpeed = speed;
        else approachSpeed = Math.max(approachSpeed, speed);
        climbAge = climbing ? 0 : climbAge + dt;
        assistScale = 0;
        if (!attended || up.y < 0.5 || groundAge > 0.65 || climbAge > 0.65) return new Vector3f();
        double fast = Math.clamp((approachSpeed - 2) / 4, 0, 1);
        assistScale = fast;
        // Uphill tangential velocity belongs to the ramp; only remove excess
        // upward momentum as the support flattens or the wheels leave it.
        double expectedRise = grounded ? speed * Math.max(0, groundSlope) : 0;
        double excess = Math.max(0, velocity.y - expectedRise - 0.3);
        double acceleration = Math.min(18, excess * 6) * fast;
        return new Vector3f(0, (float) (-mass * acceleration), 0);
    }

    Vector3f dampingTorque(Quaternion rotation, Vector3f angularVelocity, Vector3f inverseInertia) {
        Vector3f pitchAxis = VehicleContactMath.rotate(rotation, Vector3f.UNIT_X);
        Vector3f rollAxis = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Z);
        double pitchTorque = inverseInertia.x > 1E-9
                ? -angularVelocity.dot(pitchAxis) * 2 * assistScale / inverseInertia.x : 0;
        double rollTorque = inverseInertia.z > 1E-9
                ? -angularVelocity.dot(rollAxis) * 3 * assistScale / inverseInertia.z : 0;
        return pitchAxis.mult((float) pitchTorque).addLocal(rollAxis.mult((float) rollTorque));
    }
}
