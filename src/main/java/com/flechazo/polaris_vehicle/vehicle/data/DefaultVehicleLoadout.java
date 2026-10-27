package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.polaris_vehicle.part.*;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;

public final class DefaultVehicleLoadout {

    public static VehicleInstanceData create(VehicleDefinition definition) {
        VehicleInstanceData data = VehicleInstanceData.create(definition);
        InstalledPart engine = defaultEngine(definition).createPart();
        data = data.withPart(definition, VehiclePartSlot.ENGINE, engine).getOrElse(data)
                .withFuel(definition, definition.fuelCapacity());

        if (definition.supports(VehiclePartSlot.EXTRA_POWER_UNIT, VehiclePartCategory.EXTRA_POWER_UNIT)) {
            InstalledPart extraPower = VehiclePowerDefinitions.EXTRA_A.createPart();
            data = data.withPart(definition, VehiclePartSlot.EXTRA_POWER_UNIT, extraPower)
                    .getOrElse(data)
                    .withEnergy(definition, 60);
        }

        return data;
    }

    private static VehicleEngineDefinition defaultEngine(
            VehicleDefinition definition
    ) {
        if (definition.supportedPartCategories().contains(VehiclePartCategory.DRIVE_MOTOR)) {
            return VehicleEngineDefinitions.DRIVE_MOTOR;
        }

        if (definition.id().equals(VehicleDefinitions.RALLY_CAR_ID)) {
            return VehicleEngineDefinitions.LARGE;
        }
        if (definition.supportedPartCategories().contains(VehiclePartCategory.SUPER_RACING_ENGINE)) {
            return VehicleEngineDefinitions.SUPER_RACING_V;
        }
        if (definition.supportedPartCategories().contains(VehiclePartCategory.MEDIUM_ENGINE)) {
            return VehicleEngineDefinitions.MEDIUM;
        }
        if (definition.supportedPartCategories().contains(VehiclePartCategory.LARGE_ENGINE)) {
            return VehicleEngineDefinitions.LARGE;
        }
        return VehicleEngineDefinitions.SMALL;
    }
}
