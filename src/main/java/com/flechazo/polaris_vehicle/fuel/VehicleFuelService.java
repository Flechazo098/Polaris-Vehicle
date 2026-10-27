package com.flechazo.polaris_vehicle.fuel;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;

public final class VehicleFuelService {

    public static VehicleFuelTransferResult transferToVehicle(
            VehicleDefinition definition,
            VehicleInstanceData vehicleData,
            PortableFuelTankData tankData,
            int tankCapacityMl,
            int transferPerIntervalMl
    ) {
        PortableFuelTankData sanitizedTank = tankData.sanitized(tankCapacityMl);
        int vehicleSpace = Math.max(0, definition.fuelCapacity() - vehicleData.fuel());
        int transferred = VehicleFuelTypes.isAccepted(sanitizedTank.fuelType())
                ? Math.clamp(transferPerIntervalMl, 0,
                Math.min(sanitizedTank.amountMl(), vehicleSpace)
        )
                : 0;
        return new VehicleFuelTransferResult(
                vehicleData.withFuel(definition, vehicleData.fuel() + transferred),
                sanitizedTank.withAmount(sanitizedTank.amountMl() - transferred, tankCapacityMl),
                transferred
        );
    }

    public static int consumptionAmount(int normalAmountMl, double boostMultiplier, boolean boosting) {
        if (normalAmountMl <= 0) {
            return 0;
        }
        return boosting
                ? (int) Math.ceil(normalAmountMl * Math.max(1.0, boostMultiplier))
                : normalAmountMl;
    }
}
