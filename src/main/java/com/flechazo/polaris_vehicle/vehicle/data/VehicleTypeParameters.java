package com.flechazo.polaris_vehicle.vehicle.data;

import cc.sighs.oelib.data.DataManager;
import cc.sighs.oelib.data.DataRegistry;
import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

@DataDriven(
        modid = "polaris_vehicle",
        folder = "vehicle_parameters",
        syncToClient = true,
        enableCache = false,
        validator = VehicleTypeParameters.Validator.class
)
public record VehicleTypeParameters(
        VehiclePhysicalParameters physics,
        VehiclePowertrainParameters powertrain
) {
    public static final Codec<VehicleTypeParameters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VehiclePhysicalParameters.CODEC.fieldOf("physics")
                    .forGetter(VehicleTypeParameters::physics),
            VehiclePowertrainParameters.CODEC.fieldOf("powertrain")
                    .forGetter(VehicleTypeParameters::powertrain)
    ).apply(instance, VehicleTypeParameters::new));

    private static final Map<ResourceLocation, VehicleTypeParameters> DEFAULTS = defaults();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        DataRegistry.register(VehicleTypeParameters.class, CODEC);
    }

    public static VehicleTypeParameters resolve(ResourceLocation vehicleType) {
        if (registered) {
            VehicleTypeParameters loaded = DataManager.getData(VehicleTypeParameters.class, vehicleType);
            if (loaded != null) {
                return loaded;
            }
        }
        VehicleTypeParameters fallback = DEFAULTS.get(vehicleType);
        if (fallback == null) {
            throw new IllegalArgumentException("Missing vehicle parameters: " + vehicleType);
        }
        return fallback;
    }

    public static Map<ResourceLocation, VehicleTypeParameters> defaultsView() {
        return DEFAULTS;
    }

    private static Map<ResourceLocation, VehicleTypeParameters> defaults() {
        Map<ResourceLocation, VehicleTypeParameters> values = new LinkedHashMap<>();
        VehicleDefinitions.ALL.forEach((id, definition) -> {
            VehiclePhysicsProfile profile = VehiclePhysicsProfile.requireBuiltin(id);
            ChassisTuning tuning = chassisTuning(id);
            values.put(id, new VehicleTypeParameters(
                    new VehiclePhysicalParameters(
                            profile.massKilograms(),
                            tuning.centerOfMassHeightRatio(),
                            profile.suspensionRestLength(),
                            profile.suspensionMaxDroop(),
                            tuning.suspensionFrequency(),
                            tuning.compressionDamping(),
                            tuning.reboundDamping(),
                            profile.lateralGripResponse(),
                            defaultGripMultiplier(id),
                            tuning.maximumSteeringAngle(),
                            defaultStepHeight(id),
                            tuning.rollingResistance(),
                            profile.aerodynamicDragCoefficient(),
                            tuning.steeringStartRatio(),
                            tuning.minimumSteeringScale()
                    ),
                    new VehiclePowertrainParameters(
                            definition.bodyWeight(),
                            1.0,
                            tuning.engineAccelerationMultiplier(),
                            0.0,
                            0.0,
                            0.30,
                            2.0,
                            5.0,
                            tuning.brakingAcceleration()
                    )
            ));
        });
        return Map.copyOf(values);
    }

    private static double defaultStepHeight(ResourceLocation id) {
        if (id.equals(VehicleDefinitions.FORMULA_CAR_ID)) return 0.0;
        return id.equals(VehicleDefinitions.KART_ID) ? 0.5 : 1.0;
    }

    private static double defaultGripMultiplier(ResourceLocation id) {
        if (id.equals(VehicleDefinitions.ELDERLY_CAR_ID)) return 0.88;
        if (id.equals(VehicleDefinitions.SMALL_CAR_ID)) return 0.86;
        if (id.equals(VehicleDefinitions.SMALL_BUS_ID)) return 0.90;
        if (id.equals(VehicleDefinitions.SUV_ID)) return 0.92;
        if (id.equals(VehicleDefinitions.TWO_SEAT_CAR_ID)) return 0.96;
        if (id.equals(VehicleDefinitions.CONVERTIBLE_ID)) return 1.00;
        if (id.equals(VehicleDefinitions.RALLY_CAR_ID)) return 1.00;
        if (id.equals(VehicleDefinitions.KART_ID)) return 1.04;
        if (id.equals(VehicleDefinitions.FORMULA_CAR_ID)) {
            return 1.10;
        }
        throw new IllegalArgumentException("Missing grip tuning for " + id);
    }

    private static ChassisTuning chassisTuning(ResourceLocation id) {
        return switch (id.getPath()) {
            case "elderly_car" -> new ChassisTuning(0.12, 3.1, 1.05, 1.15, 44.16, 0.010, 0.55, 0.18, 1.0, 12);
            case "small_car" -> new ChassisTuning(0.09, 3.2, 1.10, 1.20, 44.16, 0.010, 0.55, 0.18, 1.0, 12);
            case "two_seat_car" -> new ChassisTuning(0.14, 3.1, 1.05, 1.15, 44.16, 0.010, 0.55, 0.18, 1.0, 12);
            case "small_bus" -> new ChassisTuning(0.16, 2.8, 1.10, 1.20, 44.16, 0.012, 0.55, 0.18, 1.0, 11);
            case "suv" -> new ChassisTuning(0.14, 3.0, 1.10, 1.20, 46, 0.012, 0.55, 0.18, 1.0, 12);
            case "convertible" -> new ChassisTuning(0.13, 3.2, 1.05, 1.15, 44.16, 0.009, 0.55, 0.18, 1.0, 12);
            case "rally_car" -> new ChassisTuning(0.13, 3.2, 1.05, 1.15, 44.16, 0.010, 0.55, 0.18, 1.0, 12);
            case "kart" -> new ChassisTuning(0.10, 3.8, 1.05, 1.15, 46, 0.008, 0.55, 0.18, 1.0, 14);
            case "formula_car" -> new ChassisTuning(0.09, 4.0, 1.10, 1.20, 42.32, 0.008, 0.55, 0.18, 1.0, 14);
            default -> throw new IllegalArgumentException("Missing independent-wheel tuning for " + id);
        };
    }

    private record ChassisTuning(
            double centerOfMassHeightRatio, double suspensionFrequency,
            double compressionDamping, double reboundDamping,
            double maximumSteeringAngle, double rollingResistance,
            double steeringStartRatio, double minimumSteeringScale,
            double engineAccelerationMultiplier, double brakingAcceleration
    ) {
    }

    public static final class Validator implements DataValidator<VehicleTypeParameters> {
        @Override
        public ValidationResult validate(VehicleTypeParameters data, ResourceLocation id) {
            if (!VehicleDefinitions.ALL.containsKey(id)) {
                return ValidationResult.failure("Unknown vehicle type " + id);
            }
            return ValidationResult.success();
        }
    }
}
