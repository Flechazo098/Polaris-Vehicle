package com.flechazo.polaris_vehicle.vehicle.physics;

import java.util.Collections;
import java.util.List;

public record VehicleWheelTerrain(List<WheelTerrain> wheels) {
    public VehicleWheelTerrain {
        if (wheels.size() < 3 || wheels.size() > 4) {
            throw new IllegalArgumentException("Three or four wheel terrain values are required");
        }
    }

    public static VehicleWheelTerrain normal() {
        return normal(4);
    }

    public static VehicleWheelTerrain normal(int wheelCount) {
        return uniform(WheelTerrain.NORMAL, wheelCount);
    }

    public static VehicleWheelTerrain uniform(WheelTerrain terrain) {
        return uniform(terrain, 4);
    }

    public static VehicleWheelTerrain uniform(WheelTerrain terrain, int wheelCount) {
        return new VehicleWheelTerrain(Collections.nCopies(wheelCount, terrain));
    }

    public WheelTerrain at(int index) {
        return wheels.get(index);
    }
}
