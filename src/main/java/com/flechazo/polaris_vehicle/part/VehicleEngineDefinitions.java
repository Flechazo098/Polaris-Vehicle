package com.flechazo.polaris_vehicle.part;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VehicleEngineDefinitions {
    public static final VehicleEngineDefinition DRIVE_MOTOR = definition(
            "drive_motor", VehiclePartCategory.DRIVE_MOTOR, 25.0, 4.0, 22_500, 1.0, 0.0, 3.0
    );
    public static final VehicleEngineDefinition SMALL = definition(
            "small_engine", VehiclePartCategory.SMALL_ENGINE, 25.0, 4.5, 32_400, 1.0, 0.0, 3.0
    );
    public static final VehicleEngineDefinition MEDIUM = definition(
            "medium_engine", VehiclePartCategory.MEDIUM_ENGINE, 30.0, 5.0, 40_500, 1.0, 0.0, 3.0
    );
    public static final VehicleEngineDefinition LARGE = definition(
            "large_engine", VehiclePartCategory.LARGE_ENGINE, 38.0, 5.5, 45_000, 1.0, 0.0, 3.0
    );
    public static final VehicleEngineDefinition RACING_V = definition(
            "racing_engine_type_v", VehiclePartCategory.RACING_ENGINE, 34.0, 6.0, 6_800, 2.0, 0.2, 2.25
    );
    public static final VehicleEngineDefinition RACING_A = definition(
            "racing_engine_type_a", VehiclePartCategory.RACING_ENGINE, 31.0, 8.0, 6_800, 1.0, 0.4, 1.5
    );
    public static final VehicleEngineDefinition SUPER_RACING_V = definition(
            "super_racing_engine_type_v", VehiclePartCategory.SUPER_RACING_ENGINE, 49.0, 7.0, 7_200, 2.0, 0.3, 2.25
    );
    public static final VehicleEngineDefinition SUPER_RACING_A = definition(
            "super_racing_engine_type_a", VehiclePartCategory.SUPER_RACING_ENGINE, 46.0, 9.0, 7_200, 1.0, 0.6, 1.5
    );

    public static final Map<ResourceLocation, VehicleEngineDefinition> ALL;

    static {
        Map<ResourceLocation, VehicleEngineDefinition> definitions = new LinkedHashMap<>();
        register(definitions, DRIVE_MOTOR);
        register(definitions, SMALL);
        register(definitions, MEDIUM);
        register(definitions, LARGE);
        register(definitions, RACING_V);
        register(definitions, RACING_A);
        register(definitions, SUPER_RACING_V);
        register(definitions, SUPER_RACING_A);
        ALL = Collections.unmodifiableMap(definitions);
    }

    public static Maybe<VehicleEngineDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id).flatMap(validId -> Maybe.ofNullable(ALL.get(validId)));
    }

    public static boolean isKnownEngine(InstalledPart part) {
        return find(part.partId()).filter(definition -> definition.matches(part)).isDefined();
    }

    private static VehicleEngineDefinition definition(
            String path,
            VehiclePartCategory category,
            double horsepower,
            double acceleration,
            int durability,
            double horsepowerWearPenalty,
            double accelerationWearPenalty,
            double accelerationRampSeconds
    ) {
        return definition(path, category, horsepower, acceleration, durability,
                horsepowerWearPenalty, accelerationWearPenalty, accelerationRampSeconds, 0.0);
    }

    private static VehicleEngineDefinition definition(
            String path,
            VehiclePartCategory category,
            double horsepower,
            double acceleration,
            int durability,
            double horsepowerWearPenalty,
            double accelerationWearPenalty,
            double accelerationRampSeconds,
            double maximumSpeedModifier
    ) {
        return new VehicleEngineDefinition(
                PolarisVehicle.id(path),
                category,
                horsepower,
                acceleration,
                durability,
                horsepowerWearPenalty,
                accelerationWearPenalty,
                maximumSpeedModifier,
                accelerationRampSeconds
        );
    }

    private static void register(
            Map<ResourceLocation, VehicleEngineDefinition> definitions,
            VehicleEngineDefinition definition
    ) {
        VehicleEngineDefinition previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate engine definition: " + definition.id());
        }
    }
}
