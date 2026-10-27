package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Bounded gameplay pitch assistance once the front axle has lost its support.
 *
 * <p>A short-wheelbase car meeting a ramp at speed acquires the pitch rate its
 * front axle needs to follow the slope. When the ramp is shorter than the
 * wheelbase the rear axle never lands on it to cancel that rate, so the chassis
 * leaves the ramp still rotating nose-up and can flip over backwards. Ramp
 * entry itself (front axle climbing) is untouched; only two states receive a
 * torque: fully airborne, and a rear-axle pivot with the nose rising.</p>
 */
final class VehiclePitchStability {
    /**
     * Wheel-contact flicker over seams and block edges stays unassisted.
     */
    private static final double AIR_DELAY_SECONDS = 0.03;
    private static final double AIR_RAMP_SECONDS = 0.08;
    private static final double AIR_PITCH_STIFFNESS = 16.0;
    private static final double AIR_PITCH_DAMPING = 8.0;
    private static final double MAX_FLIGHT_PITCH = Math.toRadians(20.0);
    private static final double PIVOT_DAMPING = 10.0;
    private static final double MAX_ANGULAR_ACCELERATION = 30.0;
    private double airAge;

    /**
     * @param frontSupported   a front wheel has a current support contact
     * @param rearSupported    a rear wheel has a current support contact
     * @param chassisSupported the chassis itself rests on terrain
     * @return world-space torque for this physics step, or a new zero vector
     */
    Vector3f torque(double dt, boolean attended, boolean frontSupported, boolean rearSupported,
                    boolean chassisSupported, Quaternion rotation, Vector3f velocity,
                    Vector3f angularVelocity, Vector3f inverseInertia) {
        boolean airborne = !frontSupported && !rearSupported && !chassisSupported;
        airAge = airborne ? airAge + dt : 0;
        if (!attended || dt <= 0 || inverseInertia.x <= 1E-9) return new Vector3f();
        Vector3f up = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Y);
        // Overturned cars belong to rollover recovery, not attitude control.
        if (up.y < 0.3F) return new Vector3f();
        Vector3f right = VehicleContactMath.rotate(rotation, Vector3f.UNIT_X);
        Vector3f forward = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Z);
        // Bullet's +X rotation lowers the nose; noseUpRate > 0 lifts it.
        double noseUpRate = -angularVelocity.dot(right);
        double alpha;
        if (airborne) {
            double scale = Math.clamp((airAge - AIR_DELAY_SECONDS) / AIR_RAMP_SECONDS, 0, 1);
            if (scale <= 0) return new Vector3f();
            double pitch = Math.asin(Math.clamp(forward.y, -1, 1));
            double horizontal = Math.hypot(velocity.x, velocity.z);
            // Follow the flight path, bounded so a steep launch still lands
            // nearly flat; without speed only the spin is removed.
            double target = horizontal > 2
                    ? Math.clamp(Math.atan2(velocity.y, horizontal), -MAX_FLIGHT_PITCH, MAX_FLIGHT_PITCH)
                    : pitch;
            double noseUp = AIR_PITCH_STIFFNESS * (target - pitch) - AIR_PITCH_DAMPING * noseUpRate;
            alpha = -Math.clamp(noseUp, -MAX_ANGULAR_ACCELERATION, MAX_ANGULAR_ACCELERATION) * scale;
        } else if (!frontSupported && rearSupported && noseUpRate > 0) {
            alpha = Math.min(MAX_ANGULAR_ACCELERATION, noseUpRate * PIVOT_DAMPING);
        } else {
            return new Vector3f();
        }
        return right.mult((float) (alpha / inverseInertia.x));
    }
}
