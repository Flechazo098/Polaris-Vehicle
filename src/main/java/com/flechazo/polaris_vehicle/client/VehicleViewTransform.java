package com.flechazo.polaris_vehicle.client;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class VehicleViewTransform {
    public static Quaternionf cameraRotation(Quaternionfc body, float relativeYaw, float pitch, float roll) {
        return new Quaternionf(body)
                .rotateY((float) Math.toRadians(180.0 - relativeYaw))
                .rotateX((float) Math.toRadians(-pitch))
                .rotateZ((float) Math.toRadians(-roll)).normalize();
    }

    public static Quaternionf handCorrection(Quaternionfc camera, Quaternionfc body) {
        return new Quaternionf(camera).conjugate()
                .mul(cameraRotation(body, 0, 0, 0)).normalize();
    }
}
