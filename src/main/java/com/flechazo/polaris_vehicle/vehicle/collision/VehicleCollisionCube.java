package com.flechazo.polaris_vehicle.vehicle.collision;

import net.minecraft.world.phys.AABB;

import java.util.Objects;

public record VehicleCollisionCube(
        String boneName,
        int cubeIndex,
        OrientedBoundingBox box,
        VehicleCollisionRole role
) {
    public VehicleCollisionCube {
        Objects.requireNonNull(boneName, "boneName");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(role, "role");
        if (cubeIndex < 0) {
            throw new IllegalArgumentException("cubeIndex may not be negative");
        }
    }

    public AABB localBounds() {
        return box.enclosingAabb();
    }

    public boolean solid() {
        return box.hasVolume();
    }

    public boolean participatesIn(int roleMask, boolean solidOnly) {
        return role.isIncludedIn(roleMask) && (!solidOnly || solid());
    }
}
