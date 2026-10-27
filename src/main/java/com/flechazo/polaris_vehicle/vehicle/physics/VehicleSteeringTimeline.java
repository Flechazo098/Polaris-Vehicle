package com.flechazo.polaris_vehicle.vehicle.physics;

public final class VehicleSteeringTimeline {
    private static final double RESPONSE = 80; // ~20ms half response, settles within a packet interval.
    private double time;
    private double angle;
    private double velocity;
    private double target;
    private boolean initialized;

    public void accept(double received, double seconds) {
        advance(seconds);
        target = received;
    }

    public double sample(double seconds) {
        advance(seconds);
        return angle;
    }

    public double velocity(double seconds) {
        advance(seconds);
        return velocity;
    }

    private void advance(double seconds) {
        if (!initialized) {
            initialized = true;
            time = seconds;
            return;
        }
        double dt = Math.max(0, seconds - time);
        if (dt == 0) return;
        time = seconds;
        double displacement = angle - target;
        double derivative = velocity + RESPONSE * displacement;
        double decay = Math.exp(-RESPONSE * dt);
        angle = target + (displacement + derivative * dt) * decay;
        velocity = (velocity - RESPONSE * derivative * dt) * decay;
    }
}
