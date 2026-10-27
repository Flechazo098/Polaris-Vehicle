package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.vehicle.physics.VehicleThrottleResponse;

/**
 * Gradual wheel-motor commands; all actual motion still belongs to Bullet.
 */
final class VehicleDriveResponse {
    private final VehicleThrottleResponse throttleResponse = new VehicleThrottleResponse();
    private double brake;
    private double speedTarget;
    private boolean initialized;

    record Command(double throttle, double brake, double speedTarget) {
    }

    Command update(double requestedThrottle, boolean braking, double measuredSpeed,
                   double forwardLimit, double reverseLimit, double acceleration, double brakingRate, double dt) {
        return update(requestedThrottle, braking, measuredSpeed, forwardLimit, reverseLimit,
                acceleration, brakingRate, 3.0, dt);
    }

    Command update(double requestedThrottle, boolean braking, double measuredSpeed,
                   double forwardLimit, double reverseLimit, double acceleration, double brakingRate,
                   double accelerationRampSeconds, double dt) {
        if (!initialized) {
            speedTarget = measuredSpeed;
            initialized = true;
        }
        if (braking) {
            requestedThrottle = 0;
            throttleResponse.reset();
            // Discard a stale driving target on brake entry. Thereafter keep
            // winding the brake target towards zero: resetting it to chassis
            // speed every substep cancels most of the wheel motor's braking.
            // An impact may slow the chassis sooner, so only bring the target
            // closer to zero, never back up to the rolling speed.
            if (brake == 0 || speedTarget * measuredSpeed < 0) speedTarget = measuredSpeed;
            else speedTarget = Math.copySign(Math.min(Math.abs(speedTarget), Math.abs(measuredSpeed)), measuredSpeed);
        }
        // A blocked chassis can stop while the wheel target keeps building.
        // On a direction change, discard that stale target and start from the
        // actual signed chassis speed. Moving cars still brake through zero;
        // continuing to climb in the same direction retains its motor target.
        if (requestedThrottle * speedTarget < 0) speedTarget = measuredSpeed;
        var pedal = throttleResponse.update(requestedThrottle, accelerationRampSeconds, dt);
        brake = braking ? 1 : 0;
        double desired = braking ? 0 : requestedThrottle > 0 ? forwardLimit
                                       : requestedThrottle < 0 ? -reverseLimit : measuredSpeed;
        // Never reset a running wheel's target to the chassis speed at a
        // stair impact: that creates a second braking impulse at the obstacle.
        // Targets remain bounded by the loadout limit; torque stays bounded by
        // rated acceleration, so a blocked car cannot build unbounded power.
        boolean slowing = Math.abs(desired) < Math.abs(speedTarget)
                || desired * speedTarget < 0;
        double rate = braking ? brakingRate * brake
                : slowing ? Math.min(brakingRate, 3.0) : Math.max(0, acceleration) * pedal.averageMagnitude();
        speedTarget += Math.clamp(desired - speedTarget, -rate * dt, rate * dt);
        return new Command(pedal.throttle(), brake, speedTarget);
    }
}
