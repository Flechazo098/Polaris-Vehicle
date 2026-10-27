package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;

import java.util.List;

/**
 * Maps installed wheel instances to their physical contacts' independent grip values.
 */
public final class VehicleWheelGrip {
    private VehicleWheelGrip() {
    }

    public static List<Double> coefficients(
            VehiclePhysicsProfile profile,
            VehicleInstanceData data
    ) {
        return profile.wheels().stream()
                .map(mount -> data.part(mount.slot())
                        .fold(() -> 0.15, VehicleWheelGrip::coefficient))
                .toList();
    }

    static double coefficient(InstalledPart wheel) {
        return VehicleTireDefinitions.find(wheel.partId())
                .filter(definition -> definition.matches(wheel))
                .fold(() -> 0.15, definition -> definition.gripCoefficient(wheel));
    }
}
