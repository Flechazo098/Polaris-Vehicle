package com.flechazo.polaris_vehicle.util;

import net.minecraft.util.Mth;

public final class MathUtil {
    private MathUtil() {
    }

    /**
     * Cubic transition with zero slope at both ends, clamped to the unit interval.
     */
    public static double smoothstep(double value) {
        double t = Math.clamp(value, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static float smoothstep(float value) {
        float t = Math.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Moves toward the target by at most {@code maximumChange}, without overshooting.
     */
    public static double approach(double current, double target, double maximumChange) {
        return current + Math.clamp(target - current, -maximumChange, maximumChange);
    }

    /**
     * Frame-rate independent first-order response; responseSeconds is the time constant.
     */
    public static double exponentialResponse(double deltaSeconds, double responseSeconds) {
        return -Math.expm1(-deltaSeconds / responseSeconds);
    }

    public static double exponentialApproach(
            double current, double target, double deltaSeconds, double responseSeconds
    ) {
        return current + (target - current) * exponentialResponse(deltaSeconds, responseSeconds);
    }

    /**
     * Expands a wrapped angle onto the revolution nearest to the previous continuous angle.
     */
    public static float unwrapDegrees(float reference, float received) {
        return reference + Mth.wrapDegrees(received - reference);
    }
}
