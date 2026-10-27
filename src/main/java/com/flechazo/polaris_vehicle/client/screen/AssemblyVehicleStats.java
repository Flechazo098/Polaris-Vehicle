package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinitions;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePerformanceCalculator;
import com.flechazo.polaris_vehicle.vehicle.physics.VehicleWheelTerrain;
import com.flechazo.polaris_vehicle.vehicle.physics.WheelTerrain;

public record AssemblyVehicleStats(double horsepower, double baseSpeed, double tireSpeed,
                                   double baseAcceleration, double tireAcceleration) {
    public static AssemblyVehicleStats calculate(VehicleDefinition definition, VehicleInstanceData data) {
        var engine = data.part(VehiclePartSlot.ENGINE)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .flatMap(part -> VehicleEngineDefinitions.find(part.partId())
                        .filter(value -> value.matches(part))
                        .map(value -> value.effectiveHorsepower(part)));
        if (engine.isEmpty()) {
            return new AssemblyVehicleStats(0, 0, 0, 0, 0);
        }
        VehicleInstanceData withoutTires = data;
        for (var slot : definition.wheelSlots()) {
            withoutTires = withoutTires.withoutPart(definition, slot);
        }
        var road = VehicleWheelTerrain.uniform(WheelTerrain.ROAD, definition.wheelSlots().size());
        var base = VehiclePerformanceCalculator.calculate(definition, withoutTires, road);
        var total = VehiclePerformanceCalculator.calculate(definition, data, road);
        return new AssemblyVehicleStats(
                engine.orElse(0.0) * VehicleTypeParameters.resolve(definition.id()).powertrain().engineHorsepowerMultiplier(),
                base.maxSpeedMetersPerSecond(),
                total.maxSpeedMetersPerSecond() - base.maxSpeedMetersPerSecond(),
                base.accelerationMetersPerSecondSquared(),
                total.accelerationMetersPerSecondSquared() - base.accelerationMetersPerSecondSquared());
    }
}
