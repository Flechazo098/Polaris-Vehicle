package com.flechazo.polaris_vehicle.power;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record VehiclePowerDefinition(
        ResourceLocation id,
        VehiclePartCategory category,
        double normalMaxSpeedModifier,
        double normalAccelerationModifier,
        int energyCapacity,
        int energyCost,
        int boostDurationTicks,
        int cooldownTicks,
        double boostMaxSpeedModifier,
        double boostAccelerationModifier,
        double passiveChargePerSecond,
        double brakeRecoveryEfficiency
) {
    public static final Codec<VehiclePowerDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VehiclePowerDefinition::id),
            VehiclePartCategory.CODEC.fieldOf("category").forGetter(VehiclePowerDefinition::category),
            Codec.DOUBLE.fieldOf("normal_max_speed_modifier")
                    .forGetter(VehiclePowerDefinition::normalMaxSpeedModifier),
            Codec.DOUBLE.fieldOf("normal_acceleration_modifier")
                    .forGetter(VehiclePowerDefinition::normalAccelerationModifier),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("energy_capacity")
                    .forGetter(VehiclePowerDefinition::energyCapacity),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("energy_cost")
                    .forGetter(VehiclePowerDefinition::energyCost),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("boost_duration_ticks")
                    .forGetter(VehiclePowerDefinition::boostDurationTicks),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("cooldown_ticks")
                    .forGetter(VehiclePowerDefinition::cooldownTicks),
            Codec.DOUBLE.fieldOf("boost_max_speed_modifier")
                    .forGetter(VehiclePowerDefinition::boostMaxSpeedModifier),
            Codec.DOUBLE.fieldOf("boost_acceleration_modifier")
                    .forGetter(VehiclePowerDefinition::boostAccelerationModifier),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("passive_charge_per_second")
                    .forGetter(VehiclePowerDefinition::passiveChargePerSecond),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("brake_recovery_efficiency")
                    .forGetter(VehiclePowerDefinition::brakeRecoveryEfficiency)
    ).apply(instance, VehiclePowerDefinition::new));

    public VehiclePowerDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        if (category != VehiclePartCategory.EXTRA_POWER_UNIT
                && category != VehiclePartCategory.ENERGY_RECOVERY_UNIT) {
            throw new IllegalArgumentException("Not a power-unit category: " + category);
        }
    }

    public InstalledPart createPart() {
        return InstalledPart.create(id, category, 1);
    }

    public boolean matches(InstalledPart part) {
        return id.equals(part.partId()) && category == part.category();
    }

    public boolean isExtraPowerUnit() {
        return category == VehiclePartCategory.EXTRA_POWER_UNIT;
    }

    public boolean isRecoveryUnit() {
        return category == VehiclePartCategory.ENERGY_RECOVERY_UNIT;
    }
}
