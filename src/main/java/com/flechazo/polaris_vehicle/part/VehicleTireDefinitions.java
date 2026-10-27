package com.flechazo.polaris_vehicle.part;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTireParameters;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VehicleTireDefinitions {

    public static final VehicleTireDefinition ROAD = definition(
            "road_tire", 12_000,
            List.of(0.25, 0.20, 0.15, 0.10),
            List.of(0.25, 0.20, 0.15, 0.10),
            List.of(0.0, 0.0, 0.0, 0.0),
            -2.0, 0.25, 0.25, 0.0, 1.10
    );
    public static final VehicleTireDefinition ALL_TERRAIN = definition(
            "all_terrain_tire", 15_000,
            List.of(0.25, 0.15, 0.15, 0.05),
            List.of(0.25, 0.15, 0.15, 0.05),
            List.of(0.25, 0.15, 0.15, 0.05),
            -3.0, 0.0, 0.0, 0.0, 1.08
    );
    public static final VehicleTireDefinition RUN_FLAT = definition(
            "run_flat_tire", 20_000,
            List.of(0.0, 0.0, 0.0, -0.25),
            List.of(0.0, 0.0, 0.0, -0.25),
            List.of(-1.0, -1.0, -1.0, -1.25),
            -2.0, 0.0, 0.0, 0.0, 1.02
    );
    public static final VehicleTireDefinition RACING_SPEED = definition(
            "racing_speed_tire", 700,
            List.of(0.0, 0.0, 0.0, 0.0),
            List.of(1.25, 1.07, 0.89, 0.71),
            List.of(0.0, 0.0, 0.0, 0.0),
            -0.6, 0.0, 1.25, 0.0, 1.22
    );
    public static final VehicleTireDefinition RACING_ENDURANCE = definition(
            "racing_endurance_tire", 1_450,
            List.of(0.0, 0.0, 0.0, 0.0),
            List.of(0.3, 0.24, 0.18, 0.12),
            List.of(0.0, 0.0, 0.0, 0.0),
            -0.7, 0.0, 0.5, 0.0, 1.16
    );
    /**
     * Balanced racing tire: middle durability, speed and road acceleration.
     */
    public static final VehicleTireDefinition RACING_BALANCED = definition(
            "racing_balanced_tire", 1_100,
            List.of(0.0, 0.0, 0.0, 0.0),
            List.of(0.7, 0.58, 0.46, 0.34),
            List.of(0.0, 0.0, 0.0, 0.0),
            -0.7, 0.0, 0.75, 0.0, 1.19
    );

    public static final Map<ResourceLocation, VehicleTireDefinition> ALL;

    static {
        Map<ResourceLocation, VehicleTireDefinition> definitions = new LinkedHashMap<>();
        register(definitions, ROAD);
        register(definitions, ALL_TERRAIN);
        register(definitions, RUN_FLAT);
        register(definitions, RACING_SPEED);
        register(definitions, RACING_BALANCED);
        register(definitions, RACING_ENDURANCE);
        ALL = Collections.unmodifiableMap(definitions);
    }

    public static Maybe<VehicleTireDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id).flatMap(validId -> Maybe.ofNullable(ALL.get(validId)));
    }

    public static InstalledPart applyDurabilityParameters(InstalledPart part) {
        return find(part.partId()).filter(definition -> definition.matches(part)).fold(() -> part, definition -> VehicleTireParameters.rescale(part, definition.maxDurability()));
    }

    public static boolean isKnownTire(InstalledPart part) {
        return find(part.partId()).filter(definition -> definition.matches(part)).isDefined();
    }

    private static VehicleTireDefinition definition(
            String path,
            int maxDurability,
            List<Double> normalSpeed,
            List<Double> roadSpeed,
            List<Double> softSpeed,
            double scrappedSpeed,
            double normalAcceleration,
            double roadAcceleration,
            double softAcceleration,
            double baseGrip
    ) {
        return new VehicleTireDefinition(
                PolarisVehicle.id(path),
                maxDurability,
                normalSpeed,
                roadSpeed,
                softSpeed,
                scrappedSpeed,
                normalAcceleration,
                roadAcceleration,
                softAcceleration,
                baseGrip
        );
    }

    private static void register(
            Map<ResourceLocation, VehicleTireDefinition> definitions,
            VehicleTireDefinition definition
    ) {
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate tire definition " + definition.id());
        }
    }
}
