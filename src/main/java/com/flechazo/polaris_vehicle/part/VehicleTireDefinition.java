package com.flechazo.polaris_vehicle.part;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleTireParameters;
import com.flechazo.polaris_vehicle.vehicle.physics.WheelTerrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
public record VehicleTireDefinition(
        ResourceLocation id,
        int maxDurability,
        List<Double> normalSpeedByWearStage,
        List<Double> roadSpeedByWearStage,
        List<Double> softSpeedByWearStage,
        double scrappedSpeedModifier,
        double normalAccelerationModifier,
        double roadAccelerationModifier,
        double softAccelerationModifier,
        double baseGrip
) {
    private static final Codec<List<Double>> FOUR_STAGES = Codec.DOUBLE.listOf(4, 4);

    public static final Codec<VehicleTireDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VehicleTireDefinition::id),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_durability")
                    .forGetter(VehicleTireDefinition::maxDurability),
            FOUR_STAGES.fieldOf("normal_speed_by_wear_stage")
                    .forGetter(VehicleTireDefinition::normalSpeedByWearStage),
            FOUR_STAGES.fieldOf("road_speed_by_wear_stage")
                    .forGetter(VehicleTireDefinition::roadSpeedByWearStage),
            FOUR_STAGES.fieldOf("soft_speed_by_wear_stage")
                    .forGetter(VehicleTireDefinition::softSpeedByWearStage),
            Codec.DOUBLE.fieldOf("scrapped_speed_modifier")
                    .forGetter(VehicleTireDefinition::scrappedSpeedModifier),
            Codec.DOUBLE.fieldOf("normal_acceleration_modifier")
                    .forGetter(VehicleTireDefinition::normalAccelerationModifier),
            Codec.DOUBLE.fieldOf("road_acceleration_modifier")
                    .forGetter(VehicleTireDefinition::roadAccelerationModifier),
            Codec.DOUBLE.fieldOf("soft_acceleration_modifier")
                    .forGetter(VehicleTireDefinition::softAccelerationModifier),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("base_grip")
                    .forGetter(VehicleTireDefinition::baseGrip)
    ).apply(instance, VehicleTireDefinition::new));

    public int builtinMaxDurability() {
        return maxDurability;
    }

    @Override
    public int maxDurability() {
        return VehicleTireParameters.resolve(id, maxDurability).maxDurability();
    }

    public InstalledPart createPart() {
        return InstalledPart.create(id, VehiclePartCategory.WHEEL, maxDurability());
    }

    public boolean matches(InstalledPart part) {
        return id.equals(part.partId())
                && part.category() == VehiclePartCategory.WHEEL;
    }

    public double speedModifier(InstalledPart part, WheelTerrain terrain) {
        if (!matches(part)) {
            return 0.0;
        }
        if (part.isScrapped()) {
            return scrappedSpeedModifier;
        }
        int stage = VehiclePartWear.crossedThresholds(part);
        return switch (terrain) {
            case NORMAL, GRASS -> normalSpeedByWearStage.get(stage);
            case ROAD -> roadSpeedByWearStage.get(stage);
            case SOFT, SOFT_GRASS, SAND, GRAVEL -> softSpeedByWearStage.get(stage);
        };
    }

    public double accelerationModifier(InstalledPart part, WheelTerrain terrain) {
        if (!matches(part) || part.isScrapped()) {
            return 0.0;
        }
        return switch (terrain) {
            case NORMAL, GRASS -> normalAccelerationModifier;
            case ROAD -> roadAccelerationModifier;
            case SOFT, SOFT_GRASS, SAND, GRAVEL -> softAccelerationModifier;
        };
    }

    public double gripCoefficient(InstalledPart part) {
        if (!matches(part)
                || !part.enabled()
                || !part.isFullyTightened()) {
            return 0.15;
        }
        double durabilityRatio = (double) part.durability() / part.maxDurability();
        return baseGrip * (0.72 + 0.28 * Math.clamp(durabilityRatio, 0.0, 1.0));
    }
}
