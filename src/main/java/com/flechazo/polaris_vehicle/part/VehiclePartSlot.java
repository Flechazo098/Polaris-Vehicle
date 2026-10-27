package com.flechazo.polaris_vehicle.part;

import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.mojang.serialization.Codec;

public enum VehiclePartSlot {
    FRONT_LEFT_WHEEL,
    FRONT_RIGHT_WHEEL,
    FRONT_CENTER_WHEEL,
    REAR_LEFT_WHEEL,
    REAR_RIGHT_WHEEL,
    ENGINE,
    SECONDARY_ENGINE,
    EXTRA_POWER_UNIT,
    ENERGY_RECOVERY_UNIT;

    public static final Codec<VehiclePartSlot> CODEC = CodecUtil.enumCodec(VehiclePartSlot.class);

    public boolean accepts(VehiclePartCategory category) {
        return switch (this) {
            case FRONT_LEFT_WHEEL, FRONT_RIGHT_WHEEL, FRONT_CENTER_WHEEL,
                 REAR_LEFT_WHEEL, REAR_RIGHT_WHEEL -> category == VehiclePartCategory.WHEEL;
            case ENGINE, SECONDARY_ENGINE -> switch (category) {
                case DRIVE_MOTOR, SMALL_ENGINE, MEDIUM_ENGINE, LARGE_ENGINE,
                     RACING_ENGINE, SUPER_RACING_ENGINE -> true;
                default -> false;
            };
            case EXTRA_POWER_UNIT -> category == VehiclePartCategory.EXTRA_POWER_UNIT;
            case ENERGY_RECOVERY_UNIT -> category == VehiclePartCategory.ENERGY_RECOVERY_UNIT;
        };
    }

    public boolean isWheel() {
        return this == FRONT_LEFT_WHEEL
                || this == FRONT_RIGHT_WHEEL
                || this == FRONT_CENTER_WHEEL
                || this == REAR_LEFT_WHEEL
                || this == REAR_RIGHT_WHEEL;
    }
}
