package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleWheelMount;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Engine sound source at the vehicle's engine bay instead of its origin.
 * Offsets use the wheel-mount model space (+Z front), so they follow the
 * body rotation including pitch and roll.
 */
final class VehicleEngineSoundPosition {
    enum Layout {FRONT, MID, REAR}

    private static final double HEIGHT_ABOVE_AXLE = 0.25;
    private static final Map<ResourceLocation, Layout> LAYOUTS = Map.of(
            VehicleDefinitions.KART_ID, Layout.REAR,
            VehicleDefinitions.SMALL_BUS_ID, Layout.REAR,
            VehicleDefinitions.FORMULA_CAR_ID, Layout.MID
    );

    private VehicleEngineSoundPosition() {
    }

    static Layout layout(ResourceLocation vehicleType) {
        return LAYOUTS.getOrDefault(vehicleType, Layout.FRONT);
    }

    static Vector3f localOffset(VehiclePhysicsProfile profile) {
        double frontZ = profile.wheels().stream().filter(VehicleWheelMount::steerable)
                .mapToDouble(wheel -> wheel.wheelCenter().z).average().orElse(0);
        double rearZ = profile.wheels().stream().filter(wheel -> !wheel.steerable())
                .mapToDouble(wheel -> wheel.wheelCenter().z).average().orElse(0);
        double axleY = profile.wheels().stream()
                .mapToDouble(wheel -> wheel.wheelCenter().y).average().orElse(0);
        double z = switch (layout(profile.vehicleType())) {
            case FRONT -> frontZ;
            case MID -> rearZ * 0.5;
            case REAR -> rearZ;
        };
        return new Vector3f(0, (float) (axleY + HEIGHT_ABOVE_AXLE), (float) z);
    }

    /**
     * Sounds update once per tick, while the rendered car moves from the
     * previous tick's pose to the current one over the next 50 ms. The
     * midpoint keeps the source on the visible car.
     */
    static Vec3 world(AbstractVehicleEntity vehicle) {
        Vector3f offset = vehicle.vehicleBodyRenderRotation(0.5F)
                .transform(localOffset(VehiclePhysicsProfile.require(vehicle.vehicleTypeId())));
        return new Vec3(
                (vehicle.xo + vehicle.getX()) * 0.5 + offset.x,
                (vehicle.yo + vehicle.getY()) * 0.5 + offset.y,
                (vehicle.zo + vehicle.getZ()) * 0.5 + offset.z
        );
    }
}
