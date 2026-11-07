package com.flechazo.polaris_vehicle.config;

import cc.sighs.oelib.config.ConfigManager;
import cc.sighs.oelib.config.ConfigSchema;
import cc.sighs.oelib.config.ConfigUnit;
import cc.sighs.oelib.config.field.ConfigField;
import cc.sighs.oelib.config.model.ConfigStorageFormat;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.license.DrivingLicenseGrade;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;

public record VehicleLicenseConfig(
        boolean enabled,
        Map<String, DrivingLicenseGrade> requiredGrades
) {
    public static final Codec<VehicleLicenseConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(VehicleLicenseConfig::enabled),
            Codec.unboundedMap(Codec.STRING, DrivingLicenseGrade.CODEC).fieldOf("required_grades")
                    .forGetter(VehicleLicenseConfig::requiredGrades)
    ).apply(instance, VehicleLicenseConfig::new));

    public static final ConfigUnit<VehicleLicenseConfig> UNIT = ConfigSchema.defineServer(
            MethodHandles.lookup(),
            PolarisVehicle.id("driving_license"),
            VehicleLicenseConfig.class,
            meta -> meta
                    .directory(PolarisVehicle.MODID)
                    .fileName("driving_license")
                    .format(ConfigStorageFormat.TOML),
            schema -> schema.group(
                    ConfigField.bool("enabled")
                            .defaultValue(true)
                            .comment("Players need an activated, unrevoked driving licence to start a vehicle's engine.")
                            .forGetter(VehicleLicenseConfig::enabled),
                    ConfigField.map("required_grades", Codec.STRING, DrivingLicenseGrade.CODEC)
                            .defaultValue(defaultGrades())
                            .comment("Lowest licence grade (C, B, A, S) that may start each vehicle. Unlisted vehicles need C.")
                            .forGetter(VehicleLicenseConfig::requiredGrades)
            ).apply(schema, VehicleLicenseConfig::new)
    );

    private static Map<String, DrivingLicenseGrade> defaultGrades() {
        Map<String, DrivingLicenseGrade> grades = new LinkedHashMap<>();
        for (ResourceLocation id : VehicleDefinitions.ALL.keySet()) grades.put(id.getPath(), DrivingLicenseGrade.C);
        return grades;
    }

    public DrivingLicenseGrade requiredGrade(ResourceLocation vehicleType) {
        return requiredGrades.getOrDefault(vehicleType.getPath(), DrivingLicenseGrade.C);
    }

    public static void register() {
        ConfigManager.registerServer(UNIT, null);
    }

    public static VehicleLicenseConfig get() {
        return UNIT.get();
    }
}
