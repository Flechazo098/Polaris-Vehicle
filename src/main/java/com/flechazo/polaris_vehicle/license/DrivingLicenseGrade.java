package com.flechazo.polaris_vehicle.license;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.Optional;

/** Licence grades from lowest to highest; a grade allows every vehicle that needs it or less. */
public enum DrivingLicenseGrade implements StringRepresentable {
    C, B, A, S;

    public static final Codec<DrivingLicenseGrade> CODEC = StringRepresentable.fromEnum(DrivingLicenseGrade::values);

    public boolean allows(DrivingLicenseGrade required) {
        return compareTo(required) >= 0;
    }

    public Optional<DrivingLicenseGrade> next() {
        return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
    }

    public static Optional<DrivingLicenseGrade> parse(String name) {
        for (var grade : values()) {
            if (grade.name().equalsIgnoreCase(name)) return Optional.of(grade);
        }
        return Optional.empty();
    }

    @Override
    public String getSerializedName() {
        return name();
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
