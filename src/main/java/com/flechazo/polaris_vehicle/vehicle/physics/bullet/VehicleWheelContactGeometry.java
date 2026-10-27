package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;

import java.util.List;

/**
 * Resolves the physical radius of every installed tire against its vehicle-specific fit.
 */
public final class VehicleWheelContactGeometry {
    private VehicleWheelContactGeometry() {
    }

    public static List<Double> radii(
            VehiclePhysicsProfile profile,
            VehicleInstanceData data
    ) {
        return profile.wheels().stream()
                .map(mount -> radius(profile, data, mount))
                .toList();
    }

    /**
     * The installed tire's own fitted tread radius; the reference road tire without one.
     */
    public static double radius(
            VehiclePhysicsProfile profile,
            VehicleInstanceData data,
            VehicleWheelMount mount
    ) {
        return data.part(mount.slot())
                .flatMap(part -> VehicleTireDefinitions.find(part.partId())
                        .filter(definition -> definition.matches(part)))
                .fold(
                        profile::tireRadius,
                        definition -> VehicleTireFit.wheel(profile.vehicleType(),
                                VehicleTireFit.styleFor(definition.id()), mount.slot()).radius()
                );
    }

    public static double averageInstalledRadius(
            VehiclePhysicsProfile profile,
            VehicleInstanceData data
    ) {
        return profile.wheels().stream()
                .filter(mount -> data.part(mount.slot()).isDefined())
                .mapToDouble(mount -> radius(profile, data, mount))
                .average()
                .orElse(profile.tireRadius());
    }
}
