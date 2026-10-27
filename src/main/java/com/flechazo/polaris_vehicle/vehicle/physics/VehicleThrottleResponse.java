package com.flechazo.polaris_vehicle.vehicle.physics;

public final class VehicleThrottleResponse {
    private double throttle;

    public record Sample(double throttle, double averageMagnitude) {
    }

    public Sample update(double requestedThrottle, double rampSeconds, double dt) {
        if (dt <= 0) return new Sample(throttle, Math.abs(throttle));
        requestedThrottle = Math.clamp(requestedThrottle, -1.0, 1.0);
        // Reversing establishes new force from zero, never from the old direction.
        if (requestedThrottle * throttle < 0) throttle = 0;
        double previous = throttle;
        double delta = requestedThrottle - throttle;
        double average;
        if (Math.abs(requestedThrottle) > Math.abs(throttle)) {
            double ramp = Math.max(0.05, rampSeconds);
            throttle += Math.copySign(Math.min(Math.abs(delta), dt / ramp), delta);
            // Integrate both the linear ramp and any remaining full-force time
            // within this tick, avoiding a physics-frequency-dependent buildup.
            double buildingTime = Math.min(dt, Math.abs(delta) * ramp);
            average = (Math.abs(previous) + Math.abs(throttle)) * 0.5 * buildingTime / dt
                    + Math.abs(throttle) * (dt - buildingTime) / dt;
        } else {
            // Release is deliberately much faster than engine force buildup.
            double remaining = Math.exp(-dt / 0.10);
            throttle += delta * (1 - remaining);
            average = Math.abs(requestedThrottle + (previous - requestedThrottle)
                    * 0.10 * (1 - remaining) / dt);
        }
        return new Sample(throttle, average);
    }

    public void reset() {
        throttle = 0;
    }
}
