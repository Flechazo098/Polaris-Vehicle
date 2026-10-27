package com.flechazo.polaris_vehicle.network;

public final class VehicleActionRateLimit {
    public static final int ONE_SHOT_INTERVAL_TICKS = 4;

    public static boolean allows(long currentTick, long previousAcceptedTick, int intervalTicks) {
        return intervalTicks >= 0
                && currentTick >= previousAcceptedTick
                && currentTick - previousAcceptedTick >= intervalTicks;
    }
}
