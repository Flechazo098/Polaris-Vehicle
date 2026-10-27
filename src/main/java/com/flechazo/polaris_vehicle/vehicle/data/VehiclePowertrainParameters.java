package com.flechazo.polaris_vehicle.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record VehiclePowertrainParameters(
        double bodyWeightPenalty,
        double engineHorsepowerMultiplier,
        double engineAccelerationMultiplier,
        double baseMaxSpeedModifier,
        double baseAccelerationModifier,
        double reverseSpeedRatio,
        double minimumReverseSpeed,
        double maximumReverseSpeed,
        double brakingMetersPerSecondSquared
) {
    public static final Codec<VehiclePowertrainParameters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0, 200.0).fieldOf("body_weight_penalty")
                    .forGetter(VehiclePowertrainParameters::bodyWeightPenalty),
            Codec.doubleRange(0.1, 5.0).fieldOf("engine_horsepower_multiplier")
                    .forGetter(VehiclePowertrainParameters::engineHorsepowerMultiplier),
            Codec.doubleRange(0.1, 5.0).fieldOf("engine_acceleration_multiplier")
                    .forGetter(VehiclePowertrainParameters::engineAccelerationMultiplier),
            Codec.doubleRange(-100.0, 100.0).fieldOf("base_max_speed_modifier")
                    .forGetter(VehiclePowertrainParameters::baseMaxSpeedModifier),
            Codec.doubleRange(-50.0, 50.0).fieldOf("base_acceleration_modifier")
                    .forGetter(VehiclePowertrainParameters::baseAccelerationModifier),
            Codec.doubleRange(0.05, 1.0).fieldOf("reverse_speed_ratio")
                    .forGetter(VehiclePowertrainParameters::reverseSpeedRatio),
            Codec.doubleRange(0.0, 30.0).fieldOf("minimum_reverse_speed")
                    .forGetter(VehiclePowertrainParameters::minimumReverseSpeed),
            Codec.doubleRange(0.0, 50.0).fieldOf("maximum_reverse_speed")
                    .forGetter(VehiclePowertrainParameters::maximumReverseSpeed),
            Codec.doubleRange(0.1, 50.0).fieldOf("braking_meters_per_second_squared")
                    .forGetter(VehiclePowertrainParameters::brakingMetersPerSecondSquared)
    ).apply(instance, VehiclePowertrainParameters::new));

    public VehiclePowertrainParameters {
        if (minimumReverseSpeed > maximumReverseSpeed) {
            throw new IllegalArgumentException(
                    "minimum_reverse_speed cannot exceed maximum_reverse_speed"
            );
        }
    }
}
