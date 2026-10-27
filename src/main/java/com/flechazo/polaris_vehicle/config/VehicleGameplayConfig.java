package com.flechazo.polaris_vehicle.config;

import cc.sighs.oelib.config.ConfigManager;
import cc.sighs.oelib.config.ConfigSchema;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.lang.invoke.MethodHandles;

public record VehicleGameplayConfig(
        int wheelDurabilityDrainIntervalTicks,
        int wheelDurabilityDrainAmount,
        int engineDurabilityDrainIntervalTicks,
        int engineDurabilityDrainAmount,
        int movingFuelDrainIntervalTicks,
        int movingFuelDrainMl,
        double boostFuelMultiplier
) {
    public static final Codec<VehicleGameplayConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 1200).fieldOf("wheel_durability_drain_interval_ticks")
                    .forGetter(VehicleGameplayConfig::wheelDurabilityDrainIntervalTicks),
            Codec.intRange(0, 1000).fieldOf("wheel_durability_drain_amount")
                    .forGetter(VehicleGameplayConfig::wheelDurabilityDrainAmount),
            Codec.intRange(1, 1200).fieldOf("engine_durability_drain_interval_ticks")
                    .forGetter(VehicleGameplayConfig::engineDurabilityDrainIntervalTicks),
            Codec.intRange(0, 1000).fieldOf("engine_durability_drain_amount")
                    .forGetter(VehicleGameplayConfig::engineDurabilityDrainAmount),
            Codec.intRange(1, 1200).fieldOf("moving_fuel_drain_interval_ticks")
                    .forGetter(VehicleGameplayConfig::movingFuelDrainIntervalTicks),
            Codec.intRange(0, 1000).fieldOf("moving_fuel_drain_ml")
                    .forGetter(VehicleGameplayConfig::movingFuelDrainMl),
            Codec.doubleRange(1.0, 100.0).fieldOf("boost_fuel_multiplier")
                    .forGetter(VehicleGameplayConfig::boostFuelMultiplier)
    ).apply(instance, VehicleGameplayConfig::new));

    public static final ConfigUnit<VehicleGameplayConfig> UNIT = ConfigSchema.defineServer(
            MethodHandles.lookup(),
            PolarisVehicle.id("gameplay"),
            VehicleGameplayConfig.class,
            meta -> meta
                    .directory(PolarisVehicle.MODID)
                    .fileName("gameplay")
                    .format(ConfigStorageFormat.TOML),
            schema -> schema.group(
                    ConfigField.intRange("wheel_durability_drain_interval_ticks", 1, 1200)
                            .defaultValue(20)
                            .comment("Ticks between wheel durability drains while the vehicle is moving.")
                            .forGetter(VehicleGameplayConfig::wheelDurabilityDrainIntervalTicks),
                    ConfigField.intRange("wheel_durability_drain_amount", 0, 1000)
                            .defaultValue(1)
                            .comment("Durability removed from every installed wheel per drain interval.")
                            .forGetter(VehicleGameplayConfig::wheelDurabilityDrainAmount),
                    ConfigField.intRange("engine_durability_drain_interval_ticks", 1, 1200)
                            .defaultValue(20)
                            .comment("Ticks between engine durability drains while the engine is running.")
                            .forGetter(VehicleGameplayConfig::engineDurabilityDrainIntervalTicks),
                    ConfigField.intRange("engine_durability_drain_amount", 0, 1000)
                            .defaultValue(1)
                            .comment("Engine durability removed per drain interval.")
                            .forGetter(VehicleGameplayConfig::engineDurabilityDrainAmount),
                    ConfigField.intRange("moving_fuel_drain_interval_ticks", 1, 1200)
                            .defaultValue(20)
                            .comment("Ticks between fuel drains while a vehicle is moving.")
                            .forGetter(VehicleGameplayConfig::movingFuelDrainIntervalTicks),
                    ConfigField.intRange("moving_fuel_drain_ml", 0, 1000)
                            .defaultValue(1)
                            .comment("Fuel in millilitres removed per moving drain interval.")
                            .forGetter(VehicleGameplayConfig::movingFuelDrainMl),
                    ConfigField.doubleRange("boost_fuel_multiplier", 1.0, 100.0)
                            .defaultValue(4.0)
                            .comment("Fuel consumption multiplier while extra power is active.")
                            .forGetter(VehicleGameplayConfig::boostFuelMultiplier)
            ).apply(schema, VehicleGameplayConfig::new)
    );

    public static void register() {
        ConfigManager.registerServer(UNIT, null);
    }

    public static VehicleGameplayConfig get() {
        return UNIT.get();
    }
}
