package com.flechazo.polaris_vehicle.part;

public final class VehiclePartWear {
    public static int crossedThresholds(InstalledPart part) {
        long durability = part.durability();
        long maximum = part.maxDurability();
        if (durability * 4L <= maximum) {
            return 3;
        }
        if (durability * 2L <= maximum) {
            return 2;
        }
        if (durability * 4L <= maximum * 3L) {
            return 1;
        }
        return 0;
    }
}
