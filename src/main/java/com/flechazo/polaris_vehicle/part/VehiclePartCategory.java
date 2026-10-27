package com.flechazo.polaris_vehicle.part;

import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.mojang.serialization.Codec;

public enum VehiclePartCategory {
    WHEEL,
    DRIVE_MOTOR,
    SMALL_ENGINE,
    MEDIUM_ENGINE,
    LARGE_ENGINE,
    RACING_ENGINE,
    SUPER_RACING_ENGINE,
    EXTRA_POWER_UNIT,
    ENERGY_RECOVERY_UNIT;

    public static final Codec<VehiclePartCategory> CODEC = CodecUtil.enumCodec(VehiclePartCategory.class);

    public boolean isWorkbenchRepairable() {
        return switch (this) {
            case WHEEL, DRIVE_MOTOR, SMALL_ENGINE, MEDIUM_ENGINE, LARGE_ENGINE,
                 RACING_ENGINE, SUPER_RACING_ENGINE -> true;
            case EXTRA_POWER_UNIT, ENERGY_RECOVERY_UNIT -> false;
        };
    }
}
