package com.flechazo.polaris_vehicle.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
public record VehicleEngineDefinition(
        ResourceLocation id,
        VehiclePartCategory category,
        double maximumHorsepower,
        double acceleration,
        int maxDurability,
        double horsepowerPenaltyPerWearStage,
        double accelerationPenaltyPerWearStage,
        double maximumSpeedModifier,
        double accelerationRampSeconds
) {
    public static final Codec<VehicleEngineDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VehicleEngineDefinition::id),
            VehiclePartCategory.CODEC.fieldOf("category").forGetter(VehicleEngineDefinition::category),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("maximum_horsepower")
                    .forGetter(VehicleEngineDefinition::maximumHorsepower),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("acceleration")
                    .forGetter(VehicleEngineDefinition::acceleration),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_durability")
                    .forGetter(VehicleEngineDefinition::maxDurability),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("horsepower_penalty_per_wear_stage")
                    .forGetter(VehicleEngineDefinition::horsepowerPenaltyPerWearStage),
            Codec.doubleRange(0.0, Double.MAX_VALUE).optionalFieldOf(
                    "acceleration_penalty_per_wear_stage",
                    0.0
            ).forGetter(VehicleEngineDefinition::accelerationPenaltyPerWearStage),
            Codec.DOUBLE.optionalFieldOf("maximum_speed_modifier", 0.0)
                    .forGetter(VehicleEngineDefinition::maximumSpeedModifier),
            Codec.doubleRange(0.05, 60.0).optionalFieldOf("acceleration_ramp_seconds", 3.0)
                    .forGetter(VehicleEngineDefinition::accelerationRampSeconds)
    ).apply(instance, VehicleEngineDefinition::new));

    public VehicleEngineDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (!isEngineCategory(category)) {
            throw new IllegalArgumentException("Not an engine category: " + category);
        }
        if (!Double.isFinite(maximumSpeedModifier) || !Double.isFinite(accelerationRampSeconds)
                || accelerationRampSeconds < 0.05 || accelerationRampSeconds > 60.0) {
            throw new IllegalArgumentException("Invalid engine speed modifier or acceleration ramp");
        }
    }

    public InstalledPart createPart() {
        return InstalledPart.create(id, category, maxDurability);
    }

    public boolean matches(InstalledPart part) {
        return id.equals(part.partId()) && category == part.category();
    }

    public double effectiveHorsepower(InstalledPart part) {
        if (!matches(part) || part.isScrapped() || !part.enabled()) {
            return 0.0;
        }
        return Math.max(
                0.0,
                maximumHorsepower - horsepowerPenaltyPerWearStage * VehiclePartWear.crossedThresholds(part)
        );
    }

    public double effectiveAcceleration(InstalledPart part) {
        if (!matches(part) || part.isScrapped() || !part.enabled()) {
            return 0.0;
        }
        return Math.max(
                0.0,
                acceleration - accelerationPenaltyPerWearStage * VehiclePartWear.crossedThresholds(part)
        );
    }

    private static boolean isEngineCategory(VehiclePartCategory category) {
        return switch (category) {
            case DRIVE_MOTOR, SMALL_ENGINE, MEDIUM_ENGINE, LARGE_ENGINE,
                 RACING_ENGINE, SUPER_RACING_ENGINE -> true;
            default -> false;
        };
    }
}
