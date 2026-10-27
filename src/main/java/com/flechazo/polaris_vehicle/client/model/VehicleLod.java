package com.flechazo.polaris_vehicle.client.model;

import software.bernie.geckolib.cache.object.GeoCube;

public final class VehicleLod {

    public static int select(double distanceSquared, int previous, boolean fullDetail, double distanceScale) {
        if (fullDetail) return 0;
        double scale = Math.clamp(distanceScale, .5, 4);
        double near = (previous == 0 ? 48 : 42) * scale;
        double far = (previous == 2 ? 84 : 96) * scale;
        if (distanceSquared <= near * near) return 0;
        return distanceSquared <= far * far ? 1 : 2;
    }

    public static boolean retain(GeoCube cube, int level) {
        if (level == 0) return true;
        double longest = Math.max(Math.abs(cube.size().x),
                Math.max(Math.abs(cube.size().y), Math.abs(cube.size().z)));
        // Long thin body panels/glass survive. Only entire subpixel details
        // are omitted; retained faces keep their authored vertices and UVs.
        return longest + 2 * Math.max(0, cube.inflate()) > (level == 1 ? 2 : 4);
    }
}
