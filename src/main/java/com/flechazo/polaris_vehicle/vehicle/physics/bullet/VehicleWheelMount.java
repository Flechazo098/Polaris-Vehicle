package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.world.phys.Vec3;

/**
 * One suspension contact in vehicle-model local coordinates.
 */
public record VehicleWheelMount(
        VehiclePartSlot slot,
        Vec3 wheelCenter,
        boolean steerable,
        boolean driven
) {

    public VehicleWheelMount {

        if (!slot.isWheel()) {
            throw new IllegalArgumentException("A wheel mount requires a wheel slot: " + slot);
        }

        if (!Double.isFinite(wheelCenter.x)
                || !Double.isFinite(wheelCenter.y)
                || !Double.isFinite(wheelCenter.z)) {
            throw new IllegalArgumentException("wheelCenter must be finite");
        }
    }
}
