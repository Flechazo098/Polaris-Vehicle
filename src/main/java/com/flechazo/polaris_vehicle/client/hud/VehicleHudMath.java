package com.flechazo.polaris_vehicle.client.hud;

import com.flechazo.polaris_vehicle.util.AnimationUtil;
import com.flechazo.polaris_vehicle.util.MathUtil;

public final class VehicleHudMath {
    public static final float SELF_TEST_RISE_TICKS = 12.0F;
    public static final float SELF_TEST_SETTLE_TICKS = 18.0F;
    public static final float SELF_TEST_TOTAL_TICKS = SELF_TEST_RISE_TICKS + SELF_TEST_SETTLE_TICKS;
    public static final double GAUGE_RESPONSE_PER_TICK = 0.45;

    public static int durabilityState(int durability, int maximumDurability, boolean engineRunning) {
        if (!engineRunning || maximumDurability <= 0 || durability <= 0) {
            return 5;
        }
        long current = durability;
        long maximum = maximumDurability;
        if (current * 4L > maximum * 3L) {
            return 1;
        }
        if (current * 2L > maximum) {
            return 2;
        }
        if (current * 4L > maximum) {
            return 3;
        }
        return 4;
    }

    public static double selfTestGauge(double actualRatio, float elapsedTicks) {
        double actual = Math.clamp(actualRatio, 0.0, 1.0);
        if (elapsedTicks < SELF_TEST_RISE_TICKS) {
            return Math.clamp(elapsedTicks / SELF_TEST_RISE_TICKS, 0.0F, 1.0F);
        }
        if (elapsedTicks >= SELF_TEST_TOTAL_TICKS) {
            return actual;
        }
        double settle = (elapsedTicks - SELF_TEST_RISE_TICKS) / SELF_TEST_SETTLE_TICKS;
        double smooth = MathUtil.smoothstep(settle);
        return 1.0 + (actual - 1.0) * smooth;
    }

    public static double fuelGauge(double ratio) {
        return Math.clamp(ratio, 0.0, 1.0);
    }

    public static double speedGauge(double metersPerSecond) {
        return Math.clamp(Math.max(0.0, metersPerSecond) / 20.0, 0.0, 1.0);
    }

    public static double interpolateSpeed(double displayed, double target, float frameDeltaTicks) {
        double current = Math.max(0, displayed), destination = Math.max(0, target);
        double next = AnimationUtil.approachPerTick(
                current, destination, frameDeltaTicks, GAUGE_RESPONSE_PER_TICK);
        return Math.abs(destination - next) < 1E-4 ? destination : next;
    }

    public static double interpolateGauge(
            double displayed,
            double target,
            float frameDeltaTicks
    ) {
        double current = Math.clamp(displayed, 0.0, 1.0);
        double destination = Math.clamp(target, 0.0, 1.0);
        double elapsed = Math.max(0.0, frameDeltaTicks);
        if (elapsed == 0.0 || current == destination) {
            return current;
        }
        double interpolated = AnimationUtil.approachPerTick(
                current, destination, elapsed, GAUGE_RESPONSE_PER_TICK);
        return Math.abs(destination - interpolated) < 1.0E-4
                ? destination
                : interpolated;
    }
}
