package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record VehicleSeatLayout(List<Vec3> seats) {

    private static final Map<ResourceLocation, VehicleSeatLayout> BY_VEHICLE;

    static {
        Map<ResourceLocation, VehicleSeatLayout> layouts = new LinkedHashMap<>();
        layouts.put(VehicleDefinitions.KART_ID, layout(
                seat(0.0, 0.1, -0.55)
        ));
        layouts.put(VehicleDefinitions.ELDERLY_CAR_ID, layout(
                seat(0.0, 0.6, -0.3)
        ));
        layouts.put(VehicleDefinitions.SMALL_BUS_ID, layout(
                seat(0.5, 0.8, 1.7),
                seat(-0.5, 0.8, 1.7),
                seat(0.5, 0.8, 0.0),
                seat(-0.5, 0.8, 0.0),
                seat(0.5, 0.8, -1.5),
                seat(-0.5, 0.8, -1.5)
        ));
        layouts.put(VehicleDefinitions.SMALL_CAR_ID, layout(
                seat(0.0, 0.6, -0.6)
        ));
        layouts.put(VehicleDefinitions.TWO_SEAT_CAR_ID, layout(
                seat(0.0, 0.8, -0.1),
                seat(0.0, 0.8, -1.4)
        ));
        layouts.put(VehicleDefinitions.SUV_ID, layout(
                seat(0.7, 0.8, 0.2),
                seat(-0.7, 0.8, 0.2),
                seat(0.7, 0.6, -1.4),
                seat(0.0, 0.6, -1.4),
                seat(-0.7, 0.6, -1.4)
        ));
        layouts.put(VehicleDefinitions.CONVERTIBLE_ID, layout(
                seat(0.6, 0.5, -0.1),
                seat(-0.6, 0.5, -0.1)
        ));
        layouts.put(VehicleDefinitions.FORMULA_CAR_ID, layout(
                seat(0.0, 0.5, -0.3)
        ));
        layouts.put(VehicleDefinitions.RALLY_CAR_ID, layout(
                seat(0.6, 0.3, 0.0),
                seat(-0.6, 0.3, 0.0)
        ));
        BY_VEHICLE = Map.copyOf(layouts);
    }

    public VehicleSeatLayout {

        if (seats.isEmpty()) {
            throw new IllegalArgumentException("A vehicle seat layout must contain at least one seat");
        }
    }

    public static Maybe<VehicleSeatLayout> find(ResourceLocation vehicleType) {
        return Maybe.ofNullable(BY_VEHICLE.get(vehicleType));
    }

    public static VehicleSeatLayout require(ResourceLocation vehicleType) {
        return find(vehicleType).fold(
                () -> {
                    throw new IllegalArgumentException(
                            "No seat layout for vehicle type " + vehicleType
                    );
                },
                layout -> layout
        );
    }

    public Vec3[] attachmentArray() {
        return seats.toArray(Vec3[]::new);
    }

    private static VehicleSeatLayout layout(Vec3... seats) {
        return new VehicleSeatLayout(List.of(seats));
    }

    private static Vec3 seat(double x, double y, double z) {
        return new Vec3(x, y, z);
    }
}
