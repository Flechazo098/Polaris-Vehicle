package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Driver-operated roll assistance for a grounded, overturned car.
 *
 * <p>The existing left/right vehicle controls become a limited roll actuator
 * after a rollover. This is a gameplay aid, not a collision or pose override:
 * the controller applies the returned world-space torque for one physics step.
 * Minie's {@code PhysicsRigidBody.applyTorque} clears torque after that step;
 * do not multiply the result by the timestep or use a torque impulse.</p>
 *
 * <p>References: FiveM's GTA control list (INPUT_VEH_MOVE_LEFT_ONLY / RIGHT_ONLY),
 * https://docs.fivem.net/docs/game-references/controls/ ; Minie rigid-body API,
 * https://stephengold.github.io/Minie/javadoc/master/com/jme3/bullet/objects/PhysicsRigidBody.html .
 * GTA's proprietary recovery implementation is not public; this actuator is
 * designed for our independent Bullet wheels and retains real terrain contacts.</p>
 */
final class VehicleRolloverRecovery {
    private static final double ENTER_UP_Y = 0.35;
    private static final double EXIT_UP_Y = 0.75;
    private static final double OVERTURN_DWELL_SECONDS = 0.25;
    private static final double CONTACT_GRACE_SECONDS = 0.12;
    private static final double MAX_HORIZONTAL_SPEED = 3.0;
    private static final double MAX_VERTICAL_SPEED = 2.0;
    private static final double TARGET_ROLL_SPEED = 1.15;
    private double overturnedSeconds;
    private double contactAge = Double.POSITIVE_INFINITY;
    private boolean recovering;

    /**
     * @param grounded            a non-suppressed support contact on any wheel or the chassis
     * @param inverseInertiaLocal chassis inverse inertia in local coordinates
     * @param halfTrackMeters     half the model's physical left/right wheel span
     * @return torque in world coordinates, or a new zero vector when inactive
     */
    Vector3f update(double dt, boolean attended, double steering, boolean grounded,
                    Quaternion rotation, Vector3f linearVelocity, Vector3f angularVelocity,
                    Vector3f inverseInertiaLocal, double massKilograms, double halfTrackMeters) {
        if (!(dt > 0) || !Double.isFinite(dt)) throw new IllegalArgumentException("Positive finite timestep required");
        Vector3f up = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Y);
        Vector3f forward = VehicleContactMath.rotate(rotation, Vector3f.UNIT_Z);
        double horizontalSpeed = Math.hypot(linearVelocity.x, linearVelocity.z);
        contactAge = grounded ? 0 : contactAge + dt;
        if (!attended || horizontalSpeed > MAX_HORIZONTAL_SPEED
                || Math.abs(linearVelocity.y) > MAX_VERTICAL_SPEED
                || Math.abs(forward.y) > 0.80 || up.y >= EXIT_UP_Y) {
            overturnedSeconds = 0;
            recovering = false;
            return new Vector3f();
        }
        if (up.y < ENTER_UP_Y && contactAge <= CONTACT_GRACE_SECONDS) overturnedSeconds += dt;
        else if (!recovering) overturnedSeconds = 0;
        if (overturnedSeconds >= OVERTURN_DWELL_SECONDS) recovering = true;
        if (!recovering || contactAge > CONTACT_GRACE_SECONDS || Math.abs(steering) < 0.05) {
            return new Vector3f();
        }

        // Positive local-Z torque rolls towards the driver's right (+input).
        // Scale by actual inertia so the bus and kart get comparable response,
        // and include the gravitational moment needed to climb off a roof edge.
        double rollInertia = inverseInertiaLocal.z > 1E-9 ? 1.0 / inverseInertiaLocal.z : 0;
        if (rollInertia <= 0 || !(massKilograms > 0) || !Double.isFinite(rollInertia)) return new Vector3f();
        double command = Math.clamp(steering, -1, 1);
        double desiredRate = command * TARGET_ROLL_SPEED;
        double actualRate = angularVelocity.dot(forward);
        double alpha = Math.clamp((desiredRate - actualRate) * 5.0, -6.0, 6.0);
        double gravityMoment = massKilograms * 9.81 * Math.max(0.15, halfTrackMeters);
        double gravityShare = Math.clamp((TARGET_ROLL_SPEED - actualRate * Math.signum(command)) / 0.40, 0, 1);
        double torque = rollInertia * alpha + command * gravityMoment * 1.10 * gravityShare;
        double limit = rollInertia * 6.0 + gravityMoment * 1.10;
        torque = Math.clamp(torque, -limit, limit);
        // Gravity assistance fades continuously as the roll rate approaches
        // its target; a hard on/off at that rate would make roof rocking jerk.
        // Let the tires and suspension finish settling onto the ground.
        double settleScale = Math.clamp((EXIT_UP_Y - up.y) / 0.30, 0, 1);
        return forward.mult((float) (torque * settleScale));
    }

    boolean active() {
        return recovering && contactAge <= CONTACT_GRACE_SECONDS;
    }
}
