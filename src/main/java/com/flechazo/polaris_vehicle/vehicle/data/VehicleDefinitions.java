package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class VehicleDefinitions {
    public static final ResourceLocation KART_ID = PolarisVehicle.id("kart");
    public static final ResourceLocation ELDERLY_CAR_ID = PolarisVehicle.id("elderly_car");
    public static final ResourceLocation SMALL_BUS_ID = PolarisVehicle.id("small_bus");
    public static final ResourceLocation SMALL_CAR_ID = PolarisVehicle.id("small_car");
    public static final ResourceLocation TWO_SEAT_CAR_ID = PolarisVehicle.id("two_seat_car");
    public static final ResourceLocation SUV_ID = PolarisVehicle.id("suv");
    public static final ResourceLocation CONVERTIBLE_ID = PolarisVehicle.id("convertible");
    public static final ResourceLocation FORMULA_CAR_ID = PolarisVehicle.id("formula_car");
    public static final ResourceLocation RALLY_CAR_ID = PolarisVehicle.id("rally_car");

    public static final Set<VehiclePartSlot> STANDARD_SLOTS = Set.of(
            VehiclePartSlot.FRONT_LEFT_WHEEL,
            VehiclePartSlot.FRONT_RIGHT_WHEEL,
            VehiclePartSlot.REAR_LEFT_WHEEL,
            VehiclePartSlot.REAR_RIGHT_WHEEL,
            VehiclePartSlot.ENGINE
    );
    public static final Set<VehiclePartSlot> THREE_WHEEL_FRONT_CENTER_SLOTS = Set.of(
            VehiclePartSlot.FRONT_CENTER_WHEEL,
            VehiclePartSlot.REAR_LEFT_WHEEL,
            VehiclePartSlot.REAR_RIGHT_WHEEL,
            VehiclePartSlot.ENGINE
    );

    public static final VehicleDefinition KART = new VehicleDefinition(
            KART_ID,
            8.0,
            1400,
            Set.of(
                    VehiclePartSlot.FRONT_LEFT_WHEEL,
                    VehiclePartSlot.FRONT_RIGHT_WHEEL,
                    VehiclePartSlot.REAR_LEFT_WHEEL,
                    VehiclePartSlot.REAR_RIGHT_WHEEL,
                    VehiclePartSlot.ENGINE,
                    VehiclePartSlot.EXTRA_POWER_UNIT,
                    VehiclePartSlot.ENERGY_RECOVERY_UNIT
            ),
            Set.of(
                    VehiclePartCategory.WHEEL,
                    VehiclePartCategory.SMALL_ENGINE,
                    VehiclePartCategory.RACING_ENGINE,
                    VehiclePartCategory.EXTRA_POWER_UNIT,
                    VehiclePartCategory.ENERGY_RECOVERY_UNIT
            )
    );

    public static final VehicleDefinition ELDERLY_CAR = standard(
            ELDERLY_CAR_ID, 10.0, 3600, VehiclePartCategory.DRIVE_MOTOR
    );
    public static final VehicleDefinition SMALL_BUS = standard(
            SMALL_BUS_ID, 22.0, 10800, VehiclePartCategory.LARGE_ENGINE
    );
    public static final VehicleDefinition SMALL_CAR = new VehicleDefinition(
            SMALL_CAR_ID,
            9.0,
            3600,
            THREE_WHEEL_FRONT_CENTER_SLOTS,
            Set.of(VehiclePartCategory.WHEEL, VehiclePartCategory.DRIVE_MOTOR)
    );
    public static final VehicleDefinition TWO_SEAT_CAR = standard(
            TWO_SEAT_CAR_ID, 10.0, 4800, VehiclePartCategory.DRIVE_MOTOR
    );
    public static final VehicleDefinition SUV = standard(
            SUV_ID, 21.0, 10800, VehiclePartCategory.LARGE_ENGINE
    );
    public static final VehicleDefinition CONVERTIBLE = standard(
            CONVERTIBLE_ID, 12.0, 7200, VehiclePartCategory.MEDIUM_ENGINE
    );
    public static final VehicleDefinition FORMULA_CAR = new VehicleDefinition(
            FORMULA_CAR_ID,
            18.0,
            1700,
            Set.of(
                    VehiclePartSlot.FRONT_LEFT_WHEEL,
                    VehiclePartSlot.FRONT_RIGHT_WHEEL,
                    VehiclePartSlot.REAR_LEFT_WHEEL,
                    VehiclePartSlot.REAR_RIGHT_WHEEL,
                    VehiclePartSlot.ENGINE,
                    VehiclePartSlot.EXTRA_POWER_UNIT,
                    VehiclePartSlot.ENERGY_RECOVERY_UNIT
            ),
            Set.of(
                    VehiclePartCategory.WHEEL,
                    VehiclePartCategory.SUPER_RACING_ENGINE,
                    VehiclePartCategory.LARGE_ENGINE,
                    VehiclePartCategory.EXTRA_POWER_UNIT,
                    VehiclePartCategory.ENERGY_RECOVERY_UNIT
            )
    );

    public static final Map<ResourceLocation, VehicleDefinition> ALL;

    public static final VehicleDefinition RALLY_CAR = new VehicleDefinition(
            RALLY_CAR_ID, 19.0, 8400, STANDARD_SLOTS,
            Set.of(VehiclePartCategory.WHEEL, VehiclePartCategory.LARGE_ENGINE,
                    VehiclePartCategory.SUPER_RACING_ENGINE)
    );

    static {
        Map<ResourceLocation, VehicleDefinition> definitions = new LinkedHashMap<>();
        register(definitions, KART);
        register(definitions, ELDERLY_CAR);
        register(definitions, SMALL_BUS);
        register(definitions, SMALL_CAR);
        register(definitions, TWO_SEAT_CAR);
        register(definitions, SUV);
        register(definitions, CONVERTIBLE);
        register(definitions, FORMULA_CAR);
        register(definitions, RALLY_CAR);
        ALL = Collections.unmodifiableMap(definitions);
    }

    public static Maybe<VehicleDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id)
                .flatMap(validId -> Maybe.ofNullable(ALL.get(validId)));
    }

    private static VehicleDefinition standard(
            ResourceLocation id,
            double bodyWeight,
            int fuelCapacity,
            VehiclePartCategory engineCategory
    ) {
        return new VehicleDefinition(
                id,
                bodyWeight,
                fuelCapacity,
                STANDARD_SLOTS,
                Set.of(VehiclePartCategory.WHEEL, engineCategory)
        );
    }

    private static void register(
            Map<ResourceLocation, VehicleDefinition> definitions,
            VehicleDefinition definition
    ) {
        VehicleDefinition previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate vehicle definition: " + definition.id());
        }
    }

}
