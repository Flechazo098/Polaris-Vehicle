package com.flechazo.polaris_vehicle.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record VehiclePhysicalParameters(
        double massKilograms,
        double centerOfMassHeightRatio,
        double suspensionRestLength,
        double suspensionMaxDroop,
        double suspensionNaturalFrequencyHertz,
        double compressionDampingRatio,
        double reboundDampingRatio,
        double lateralGripResponse,
        double tireGripMultiplier,
        double maximumSteeringAngleDegrees,
        double maximumStepHeight,
        double rollingResistanceCoefficient,
        double aerodynamicDragCoefficient,
        double steeringLimitStartRatio,
        double minimumSteeringScale
) {

    public static final Codec<VehiclePhysicalParameters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(100.0, 20_000.0).fieldOf("mass_kilograms")
                    .forGetter(VehiclePhysicalParameters::massKilograms),
            Codec.doubleRange(0.05, 0.75).fieldOf("center_of_mass_height_ratio")
                    .forGetter(VehiclePhysicalParameters::centerOfMassHeightRatio),
            Codec.doubleRange(0.02, 2.0).fieldOf("suspension_rest_length")
                    .forGetter(VehiclePhysicalParameters::suspensionRestLength),
            Codec.doubleRange(0.01, 1.0).fieldOf("suspension_max_droop")
                    .forGetter(VehiclePhysicalParameters::suspensionMaxDroop),
            Codec.doubleRange(0.5, 8.0).fieldOf("suspension_natural_frequency_hertz")
                    .forGetter(VehiclePhysicalParameters::suspensionNaturalFrequencyHertz),
            Codec.doubleRange(0.0, 3.0).fieldOf("compression_damping_ratio")
                    .forGetter(VehiclePhysicalParameters::compressionDampingRatio),
            Codec.doubleRange(0.0, 3.0).fieldOf("rebound_damping_ratio")
                    .forGetter(VehiclePhysicalParameters::reboundDampingRatio),
            Codec.doubleRange(0.1, 30.0).fieldOf("lateral_grip_response")
                    .forGetter(VehiclePhysicalParameters::lateralGripResponse),
            Codec.doubleRange(0.1, 3.0).fieldOf("tire_grip_multiplier")
                    .forGetter(VehiclePhysicalParameters::tireGripMultiplier),
            Codec.doubleRange(1.0, 80.0).fieldOf("maximum_steering_angle_degrees")
                    .forGetter(VehiclePhysicalParameters::maximumSteeringAngleDegrees),
            Codec.doubleRange(0.0, 2.0).fieldOf("maximum_step_height")
                    .forGetter(VehiclePhysicalParameters::maximumStepHeight),
            Codec.doubleRange(0.0, 1.0).fieldOf("rolling_resistance_coefficient")
                    .forGetter(VehiclePhysicalParameters::rollingResistanceCoefficient),
            Codec.doubleRange(0.0, 5.0).fieldOf("aerodynamic_drag_coefficient")
                    .forGetter(VehiclePhysicalParameters::aerodynamicDragCoefficient),
            Codec.doubleRange(0.0, 1.0).fieldOf("steering_limit_start_ratio")
                    .forGetter(VehiclePhysicalParameters::steeringLimitStartRatio),
            Codec.doubleRange(0.0, 1.0).fieldOf("minimum_steering_scale")
                    .forGetter(VehiclePhysicalParameters::minimumSteeringScale)
    ).apply(instance, VehiclePhysicalParameters::new));
}
