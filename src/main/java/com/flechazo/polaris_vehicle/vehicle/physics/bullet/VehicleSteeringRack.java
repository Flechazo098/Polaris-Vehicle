package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.util.MathUtil;

/**
 * Stateful keyboard actuator, integrated at physics frequency rather than render frequency.
 */
public final class VehicleSteeringRack {
    private double position;

    public double update(double input, double speed, boolean grounded,
                         VehicleSteeringResponse response, double dt) {
        if (!(dt > 0) || !Double.isFinite(dt)) return eased();
        double target = Math.clamp(input, -1, 1);
        if (Math.abs(target) > 1E-6) {
            if (response.instant()) position = target;
            else if (eased() * target < 0 || Math.abs(target) < Math.abs(eased())) {
                // Unwinding: the eased output is flat at full lock, so stepping
                // the position would hold the wheels there before they turn back.
                // Move the output itself; the time to centre is unchanged.
                position = positionFor(MathUtil.approach(eased(), target, dt / response.lockSeconds()));
            } else position = MathUtil.approach(position, target, dt / response.lockSeconds());
        } else if (grounded) {
            // Suppress tiny suspension/contact jitter at rest. Both the value
            // and its slope approach zero here; there is no moving-speed switch.
            double rolling = Math.max(0, Math.abs(speed) - 0.05);
            // Keep parking hold, but don't turn a .45s return into several
            // seconds of continued turning at ordinary crawl speeds.
            double aligning = rolling * rolling / (rolling * rolling + .0625);
            // Returning is unwinding too, so it starts straight away from full lock.
            position = positionFor(MathUtil.approach(eased(), 0, dt * aligning / response.returnSeconds()));
        }
        return eased();
    }

    private double eased() {
        // Same symmetric cubic used by the authored wheel/arm animation blend,
        // now applied once to the physical rack and shared with the renderer.
        return position * (1.5 - 0.5 * position * position);
    }

    private static double positionFor(double output) {
        return Math.signum(output) * positionForOutput(output);
    }

    public static double positionForOutput(double output) {
        double target = Math.clamp(Math.abs(output), 0, 1);
        double low = 0, high = 1;
        for (int i = 0; i < 48; i++) {
            double middle = (low + high) * .5;
            if (middle * (1.5 - .5 * middle * middle) < target) low = middle;
            else high = middle;
        }
        return (low + high) * .5;
    }
}
