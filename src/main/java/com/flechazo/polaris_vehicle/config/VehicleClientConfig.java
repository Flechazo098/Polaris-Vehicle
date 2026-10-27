package com.flechazo.polaris_vehicle.config;

import cc.sighs.oelib.config.ConfigManager;
import cc.sighs.oelib.config.ConfigSchema;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringResponse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.lang.invoke.MethodHandles;

public record VehicleClientConfig(
        double drivingCameraFovDegrees,
        double drivingCameraDistance,
        boolean instantSteering,
        double steeringLockSeconds,
        double steeringReturnSeconds,
        boolean vehicleLodEnabled,
        double vehicleLodDistanceScale,
        boolean parameterEditorEnabled
) {
    public static final double DEFAULT_VISIBLE_LOCK_SECONDS = 0.10;
    public static final Codec<VehicleClientConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.doubleRange(50.0, 140.0)
                            .fieldOf("driving_camera_fov_degrees")
                            .forGetter(VehicleClientConfig::drivingCameraFovDegrees),
                    Codec.doubleRange(2.0, 20.0)
                            .fieldOf("driving_camera_distance")
                            .forGetter(VehicleClientConfig::drivingCameraDistance),
                    Codec.BOOL.optionalFieldOf("instant_steering", false).forGetter(VehicleClientConfig::instantSteering),
                    Codec.doubleRange(0.05, 2.0).optionalFieldOf("steering_lock_seconds", DEFAULT_VISIBLE_LOCK_SECONDS).forGetter(VehicleClientConfig::steeringLockSeconds),
                    Codec.doubleRange(0.10, 3.0).optionalFieldOf("steering_return_seconds", 0.20).forGetter(VehicleClientConfig::steeringReturnSeconds),
                    Codec.BOOL.optionalFieldOf("vehicle_lod_enabled", true).forGetter(VehicleClientConfig::vehicleLodEnabled),
                    Codec.doubleRange(.5, 4).optionalFieldOf("vehicle_lod_distance_scale", 1.0).forGetter(VehicleClientConfig::vehicleLodDistanceScale),
                    Codec.BOOL.optionalFieldOf("parameter_editor_enabled", false).forGetter(VehicleClientConfig::parameterEditorEnabled)
            ).apply(instance, VehicleClientConfig::new)
    );

    public static final ConfigUnit<VehicleClientConfig> UNIT = ConfigSchema.defineClient(
            MethodHandles.lookup(),
            PolarisVehicle.id("client"),
            VehicleClientConfig.class,
            meta -> meta
                    .directory(PolarisVehicle.MODID)
                    .fileName("client")
                    .format(ConfigStorageFormat.TOML),
            schema -> schema.group(
                    ConfigField.doubleRange("driving_camera_fov_degrees", 50.0, 140.0)
                            .defaultValue(95.0)
                            .comment("Horizontal driving field of view in degrees.")
                            .forGetter(VehicleClientConfig::drivingCameraFovDegrees),
                    ConfigField.doubleRange("driving_camera_distance", 2.0, 20.0)
                            .defaultValue(6.5)
                            .comment("Maximum third-person distance behind the vehicle centre.")
                            .forGetter(VehicleClientConfig::drivingCameraDistance),
                    ConfigField.bool("instant_steering").defaultValue(false)
                            .comment("True: A/D immediately requests full available lock; false: progressive steering.")
                            .forGetter(VehicleClientConfig::instantSteering),
                    ConfigField.doubleRange("steering_lock_seconds", 0.05, 2.0).defaultValue(DEFAULT_VISIBLE_LOCK_SECONDS)
                            .comment("At rest: seconds from centre until the visible tires and steering-wheel animation reach lock together; calibrated per vehicle.")
                            .forGetter(VehicleClientConfig::steeringLockSeconds),
                    ConfigField.doubleRange("steering_return_seconds", 0.10, 3.0).defaultValue(0.20)
                            .comment("Moving self-centering time; slower at crawl speed, no centering while parked.")
                            .forGetter(VehicleClientConfig::steeringReturnSeconds),
                    ConfigField.bool("vehicle_lod_enabled").defaultValue(true)
                            .comment("Simplify small chassis details beyond 48/96 blocks; occupied and GUI vehicles stay full detail.")
                            .forGetter(VehicleClientConfig::vehicleLodEnabled),
                    ConfigField.doubleRange("vehicle_lod_distance_scale", .5, 4).defaultValue(1.0)
                            .comment("Multiply both LOD distances; larger values retain detail farther away.")
                            .forGetter(VehicleClientConfig::vehicleLodDistanceScale),
                    ConfigField.bool("parameter_editor_enabled").defaultValue(false)
                            .comment("Developer tool: the K key opens the vehicle parameter editor. Off by default.")
                            .forGetter(VehicleClientConfig::parameterEditorEnabled)
            ).apply(schema, VehicleClientConfig::new)
    );

    public VehicleSteeringResponse steeringResponse() {
        return new VehicleSteeringResponse(
                instantSteering, steeringLockSeconds, steeringReturnSeconds);
    }

    public static void register() {
        ConfigManager.registerClient(UNIT);
    }

    public static VehicleClientConfig get() {
        return UNIT.get();
    }
}
