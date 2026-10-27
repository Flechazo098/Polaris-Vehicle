package com.flechazo.polaris_vehicle.vehicle.seat;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionRole;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class VehicleWheelInteractionRegions {
    public static String boneName(VehiclePartSlot slot) {
        return "interaction_wheel_" + switch (slot) {
            case FRONT_LEFT_WHEEL -> "front_left";
            case FRONT_RIGHT_WHEEL -> "front_right";
            case FRONT_CENTER_WHEEL -> "front_center";
            case REAR_LEFT_WHEEL -> "rear_left";
            case REAR_RIGHT_WHEEL -> "rear_right";
            default -> throw new IllegalArgumentException("Not a wheel slot: " + slot);
        };
    }

    public static List<VehicleWheelSelector.Target> targets(VehiclePhysicsProfile profile, VehicleCollisionModel model) {
        List<VehicleWheelSelector.Target> result = new ArrayList<>();
        for (int index = 0; index < profile.wheels().size(); index++) {
            var mount = profile.wheels().get(index);
            String bone = boneName(mount.slot());
            AABB bounds = null;
            for (var cube : model.cubes()) {
                if (cube.boneName().equals(bone) && cube.role() == VehicleCollisionRole.SENSOR) {
                    bounds = bounds == null ? cube.localBounds() : bounds.minmax(cube.localBounds());
                }
            }
            if (bounds == null) throw new IllegalStateException("Missing wheel interaction bone: " + bone);
            result.add(new VehicleWheelSelector.Target(index, bounds.getCenter(), bounds));
        }
        return List.copyOf(result);
    }
}
