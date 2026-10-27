package com.flechazo.polaris_vehicle.power;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VehiclePowerDefinitions {
    public static final VehiclePowerDefinition EXTRA_A = definition(
            "extra_power_unit_a", VehiclePartCategory.EXTRA_POWER_UNIT,
            -1.0, -0.25, 60, 60, 100, 0, 10.0, 4.0, 0.0
    );
    public static final VehiclePowerDefinition EXTRA_B = definition(
            "extra_power_unit_b", VehiclePartCategory.EXTRA_POWER_UNIT,
            -1.5, -0.75, 74, 37, 60, 100, 7.0, 7.0, 0.0
    );
    public static final VehiclePowerDefinition RECOVERY_A = definition(
            "energy_recovery_unit_a", VehiclePartCategory.ENERGY_RECOVERY_UNIT,
            -1.0, -0.5, 0, 0, 0, 0, 0.0, 0.0, 0.30
    );
    public static final VehiclePowerDefinition RECOVERY_B = definition(
            "energy_recovery_unit_b", VehiclePartCategory.ENERGY_RECOVERY_UNIT,
            -0.5, -0.25, 0, 0, 0, 0, 0.0, 0.0, 0.15
    );

    public static final Map<ResourceLocation, VehiclePowerDefinition> ALL;

    static {
        Map<ResourceLocation, VehiclePowerDefinition> definitions = new LinkedHashMap<>();
        register(definitions, EXTRA_A);
        register(definitions, EXTRA_B);
        register(definitions, RECOVERY_A);
        register(definitions, RECOVERY_B);
        ALL = Collections.unmodifiableMap(definitions);
    }

    public static Maybe<VehiclePowerDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id).flatMap(validId -> Maybe.ofNullable(ALL.get(validId)));
    }

    public static boolean isKnownPowerUnit(InstalledPart part) {
        return find(part.partId()).filter(definition -> definition.matches(part)).isDefined();
    }

    private static VehiclePowerDefinition definition(
            String path,
            VehiclePartCategory category,
            double normalSpeed,
            double normalAcceleration,
            int energyCapacity,
            int energyCost,
            int durationTicks,
            int cooldownTicks,
            double boostSpeed,
            double boostAcceleration,
            double passiveCharge
    ) {
        return new VehiclePowerDefinition(
                PolarisVehicle.id(path),
                category,
                normalSpeed,
                normalAcceleration,
                energyCapacity,
                energyCost,
                durationTicks,
                cooldownTicks,
                boostSpeed,
                boostAcceleration,
                passiveCharge,
                0.0
        );
    }

    private static void register(
            Map<ResourceLocation, VehiclePowerDefinition> definitions,
            VehiclePowerDefinition definition
    ) {
        VehiclePowerDefinition previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate power definition: " + definition.id());
        }
    }
}
