package com.flechazo.polaris_vehicle.vehicle.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VehicleDismountGesture {

    public static final int DOUBLE_PRESS_TICKS = 10;

    public enum Result {NONE, FIRST_PRESS, DISMOUNT}

    private final Map<UUID, Long> presses = new HashMap<>();

    public Result press(UUID player, long gameTime) {
        Long previous = presses.get(player);
        if (previous != null && gameTime >= previous && gameTime - previous <= DOUBLE_PRESS_TICKS) {
            presses.remove(player);
            return Result.DISMOUNT;
        }
        presses.put(player, gameTime);
        return Result.FIRST_PRESS;
    }

    public void forget(UUID player) {
        presses.remove(player);
    }
}
