package com.flechazo.polaris_vehicle.vehicle.physics;

public final class VehicleSlipstreamHold {

    public static final int HOLD_TICKS = 10;
    private double heldBonus;
    private int ticksLeft;
    private double lastMeasured;

    public double update(double measured) {
        boolean leftTheWake = measured <= 0 && lastMeasured > 0;
        lastMeasured = measured;
        if (measured >= heldBonus) {
            heldBonus = measured;
            ticksLeft = measured > 0 ? HOLD_TICKS : 0;
            return measured;
        }
        if (leftTheWake) ticksLeft = HOLD_TICKS;
        if (ticksLeft > 0) {
            ticksLeft--;
            return heldBonus;
        }
        heldBonus = measured;
        ticksLeft = measured > 0 ? HOLD_TICKS : 0;
        return measured;
    }
}
