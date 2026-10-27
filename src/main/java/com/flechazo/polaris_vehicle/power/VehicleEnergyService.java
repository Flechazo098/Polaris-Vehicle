package com.flechazo.polaris_vehicle.power;

public final class VehicleEnergyService {

    public static VehicleEnergyChargeResult charge(
            int currentEnergy,
            int currentRemainderMilli,
            int capacity,
            double chargePerSecond
    ) {
        int clampedEnergy = Math.clamp(currentEnergy, 0, Math.max(0, capacity));
        if (clampedEnergy >= capacity || chargePerSecond <= 0.0) {
            return new VehicleEnergyChargeResult(clampedEnergy, 0);
        }
        long addedMilli = Math.round(chargePerSecond * 1_000.0);
        long roomMilli = (long) (capacity - clampedEnergy) * 1_000L;
        long totalMilli = Math.clamp(currentRemainderMilli, 0, 999)
                + Math.min(addedMilli, roomMilli);
        int whole = (int) Math.min(capacity - clampedEnergy, totalMilli / 1_000L);
        int newEnergy = clampedEnergy + whole;
        int remainder = newEnergy >= capacity ? 0 : (int) (totalMilli % 1_000L);
        return new VehicleEnergyChargeResult(newEnergy, remainder);
    }
}
