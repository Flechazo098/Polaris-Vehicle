package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePhysicalParameters;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal physical dimensions and suspension tuning for one supplied model.
 * Gameplay speed and acceleration remain sourced from {@code VehiclePerformance}.
 */
public record VehiclePhysicsProfile(
        ResourceLocation vehicleType,
        double massKilograms,
        double centerOfMassHeightRatio,
        List<VehicleWheelMount> wheels,
        double tireRadius,
        double suspensionRestLength,
        double suspensionMaxDroop,
        double suspensionNaturalFrequencyHertz,
        double compressionDampingRatio,
        double reboundDampingRatio,
        double lateralGripResponse,
        double maximumSteeringAngleDegrees,
        double rollingResistanceCoefficient,
        double aerodynamicDragCoefficient
) {

    private static final Map<ResourceLocation, VehiclePhysicsProfile> BUILTIN = builtins();

    private record ConfiguredProfile(VehiclePhysicalParameters parameters, VehiclePhysicsProfile profile) {
    }

    private static final Map<ResourceLocation, ConfiguredProfile> CONFIGURED = new ConcurrentHashMap<>();

    public VehiclePhysicsProfile {
        Objects.requireNonNull(vehicleType, "vehicleType");
        if (wheels.size() < 3 || wheels.size() > 4) {
            throw new IllegalArgumentException("A vehicle physics profile must contain three or four contacts");
        }
        if (wheels.stream().map(VehicleWheelMount::slot).distinct().count() != wheels.size()) {
            throw new IllegalArgumentException("A vehicle physics profile cannot repeat wheel slots");
        }
    }

    public static VehiclePhysicsProfile require(ResourceLocation vehicleType) {
        VehiclePhysicsProfile builtin = requireBuiltin(vehicleType);
        VehiclePhysicalParameters parameters = VehicleTypeParameters.resolve(vehicleType).physics();
        var cached = CONFIGURED.get(vehicleType);
        if (cached != null && cached.parameters().equals(parameters)) return cached.profile();
        var profile = new VehiclePhysicsProfile(
                vehicleType,
                parameters.massKilograms(),
                parameters.centerOfMassHeightRatio(),
                builtin.wheels(),
                builtin.tireRadius(),
                parameters.suspensionRestLength(),
                parameters.suspensionMaxDroop(),
                parameters.suspensionNaturalFrequencyHertz(),
                parameters.compressionDampingRatio(),
                parameters.reboundDampingRatio(),
                parameters.lateralGripResponse(),
                parameters.maximumSteeringAngleDegrees(),
                parameters.rollingResistanceCoefficient(),
                parameters.aerodynamicDragCoefficient()
        );
        CONFIGURED.put(vehicleType, new ConfiguredProfile(parameters, profile));
        return profile;
    }

    public static VehiclePhysicsProfile requireBuiltin(ResourceLocation vehicleType) {
        VehiclePhysicsProfile profile = BUILTIN.get(vehicleType);
        if (profile == null) {
            throw new IllegalArgumentException("Missing vehicle physics profile: " + vehicleType);
        }
        return profile;
    }

    public static Map<ResourceLocation, VehiclePhysicsProfile> builtinsView() {
        return BUILTIN;
    }

    private static Map<ResourceLocation, VehiclePhysicsProfile> builtins() {
        Map<ResourceLocation, VehiclePhysicsProfile> profiles = new LinkedHashMap<>();
        // Wheel centres and radii come from the fitted tire meshes; only the
        // chassis and suspension tuning below is authored here.
        add(profiles, profile(VehicleDefinitions.KART_ID, 750.0,
                0.20, 0.16, 3.00, 0.80, 1.00, 10.0, 34.0));
        add(profiles, profile(VehicleDefinitions.ELDERLY_CAR_ID, 900.0,
                0.25, 0.18, 2.60, 0.85, 1.05, 6.0, 28.0));
        add(profiles, profile(VehicleDefinitions.SMALL_BUS_ID, 1650.0,
                0.34, 0.23, 2.20, 0.88, 1.08, 6.5, 27.0));
        // slc_ltl has one centered steering wheel and two rear wheels.
        add(profiles, profile(VehicleDefinitions.SMALL_CAR_ID, 1150.0,
                0.26, 0.19, 2.50, 0.85, 1.05, 6.5, 26.0));
        add(profiles, profile(VehicleDefinitions.TWO_SEAT_CAR_ID, 1300.0,
                0.28, 0.20, 2.40, 0.82, 1.02, 7.5, 30.0));
        add(profiles, profile(VehicleDefinitions.SUV_ID, 1400.0,
                0.34, 0.24, 2.35, 0.88, 1.08, 7.0, 28.0));
        add(profiles, profile(VehicleDefinitions.CONVERTIBLE_ID, 1300.0,
                0.28, 0.20, 2.50, 0.82, 1.02, 8.5, 32.0));
        // Formula cars use a quick rack and high lateral response, while the
        // low center of mass and stiff suspension carry the cornering load.
        add(profiles, profile(VehicleDefinitions.FORMULA_CAR_ID, 1400.0,
                0.24, 0.17, 3.50, 0.90, 1.10, 13.0, 32.0));
        add(profiles, profile(VehicleDefinitions.RALLY_CAR_ID, 1350.0,
                0.30, 0.22, 3.20, 1.05, 1.15, 9.5, 48.0));
        return Map.copyOf(profiles);
    }

    private static VehiclePhysicsProfile profile(
            ResourceLocation id,
            double mass,
            double restLength,
            double maxDroop,
            double frequency,
            double compressionDamping,
            double reboundDamping,
            double lateralResponse,
            double steeringAngle
    ) {
        var definition = VehicleDefinitions.ALL.get(id);
        if (definition == null) {
            throw new IllegalStateException("Missing vehicle definition for physics profile " + id);
        }
        List<VehicleWheelMount> wheels = definition.wheelSlots().stream()
                .map(slot -> mount(slot, VehicleTireFit.wheel(id, VehicleTireFit.REFERENCE_STYLE, slot).center()))
                .toList();
        double radius = definition.wheelSlots().stream()
                .mapToDouble(slot -> VehicleTireFit.wheel(id, VehicleTireFit.REFERENCE_STYLE, slot).radius())
                .average().orElseThrow();
        return new VehiclePhysicsProfile(
                id,
                mass,
                defaultCenterOfMassHeightRatio(id),
                wheels,
                radius,
                restLength,
                maxDroop,
                frequency,
                compressionDamping,
                reboundDamping,
                lateralResponse,
                steeringAngle,
                defaultRollingResistance(id),
                defaultAerodynamicDrag(id)
        );
    }

    /**
     * Front-axle wheels steer; every installed wheel is driven.
     */
    private static VehicleWheelMount mount(VehiclePartSlot slot, Vec3 center) {
        boolean steerable = slot == VehiclePartSlot.FRONT_LEFT_WHEEL
                || slot == VehiclePartSlot.FRONT_RIGHT_WHEEL
                || slot == VehiclePartSlot.FRONT_CENTER_WHEEL;
        return new VehicleWheelMount(slot, center, steerable, true);
    }

    private static double defaultCenterOfMassHeightRatio(ResourceLocation id) {
        if (id.equals(VehicleDefinitions.KART_ID)) return 0.18;
        if (id.equals(VehicleDefinitions.ELDERLY_CAR_ID)) return 0.20;
        if (id.equals(VehicleDefinitions.SMALL_BUS_ID)) return 0.24;
        if (id.equals(VehicleDefinitions.SMALL_CAR_ID)) return 0.19;
        if (id.equals(VehicleDefinitions.TWO_SEAT_CAR_ID)) return 0.22;
        if (id.equals(VehicleDefinitions.SUV_ID)) return 0.21;
        if (id.equals(VehicleDefinitions.CONVERTIBLE_ID)) return 0.22;
        if (id.equals(VehicleDefinitions.FORMULA_CAR_ID)) return 0.17;
        if (id.equals(VehicleDefinitions.RALLY_CAR_ID)) return 0.13;
        throw new IllegalArgumentException("Missing center-of-mass tuning for " + id);
    }

    private static double defaultRollingResistance(ResourceLocation id) {
        if (id.equals(VehicleDefinitions.KART_ID)) return 0.018;
        if (id.equals(VehicleDefinitions.ELDERLY_CAR_ID)) return 0.022;
        if (id.equals(VehicleDefinitions.SMALL_BUS_ID)
                || id.equals(VehicleDefinitions.SMALL_CAR_ID)
                || id.equals(VehicleDefinitions.SUV_ID)) return 0.020;
        if (id.equals(VehicleDefinitions.TWO_SEAT_CAR_ID)) return 0.017;
        if (id.equals(VehicleDefinitions.CONVERTIBLE_ID)) return 0.016;
        if (id.equals(VehicleDefinitions.FORMULA_CAR_ID)) return 0.014;
        if (id.equals(VehicleDefinitions.RALLY_CAR_ID)) return 0.010;
        throw new IllegalArgumentException("Missing rolling-resistance tuning for " + id);
    }

    private static double defaultAerodynamicDrag(ResourceLocation id) {
        if (id.equals(VehicleDefinitions.KART_ID)) return 0.32;
        if (id.equals(VehicleDefinitions.ELDERLY_CAR_ID)) return 0.55;
        if (id.equals(VehicleDefinitions.SMALL_BUS_ID)) return 0.75;
        if (id.equals(VehicleDefinitions.SMALL_CAR_ID)) return 0.50;
        if (id.equals(VehicleDefinitions.TWO_SEAT_CAR_ID)) return 0.48;
        if (id.equals(VehicleDefinitions.SUV_ID)) return 0.65;
        if (id.equals(VehicleDefinitions.CONVERTIBLE_ID)) return 0.38;
        if (id.equals(VehicleDefinitions.FORMULA_CAR_ID)) return 0.30;
        if (id.equals(VehicleDefinitions.RALLY_CAR_ID)) return 0.40;
        throw new IllegalArgumentException("Missing aerodynamic tuning for " + id);
    }

    private static void add(
            Map<ResourceLocation, VehiclePhysicsProfile> profiles,
            VehiclePhysicsProfile profile
    ) {
        var definition = VehicleDefinitions.ALL.get(profile.vehicleType());
        if (definition == null) {
            throw new IllegalStateException("Missing vehicle definition for physics profile "
                    + profile.vehicleType());
        }
        List<VehiclePartSlot> physicalSlots = profile.wheels().stream()
                .map(VehicleWheelMount::slot)
                .toList();
        if (!physicalSlots.equals(definition.wheelSlots())) {
            throw new IllegalStateException("Physics wheel mounts " + physicalSlots
                    + " do not match definition wheel slots " + definition.wheelSlots()
                    + " for " + profile.vehicleType());
        }
        if (profiles.putIfAbsent(profile.vehicleType(), profile) != null) {
            throw new IllegalStateException("Duplicate vehicle physics profile " + profile.vehicleType());
        }
    }
}
