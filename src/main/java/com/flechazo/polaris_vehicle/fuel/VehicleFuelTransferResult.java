package com.flechazo.polaris_vehicle.fuel;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;

public record VehicleFuelTransferResult(
        VehicleInstanceData vehicleData,
        PortableFuelTankData tankData,
        int transferredMl
) {
}
