package com.flechazo.polaris_vehicle.vehicle.entity;

public final class VehicleJackAnimationTiming {
    public static final double LENGTH_TICKS = .2917 * 20;
    public static final int COMPLETION_TICKS = (int) Math.ceil(LENGTH_TICKS);

    public static double tick(double elapsed, boolean lowering) {
        double age = Math.clamp(elapsed, 0, LENGTH_TICKS);
        return lowering ? LENGTH_TICKS - age : age;
    }
}
