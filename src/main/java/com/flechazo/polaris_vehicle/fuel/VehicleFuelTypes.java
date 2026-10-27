package com.flechazo.polaris_vehicle.fuel;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class VehicleFuelTypes {
    public static final ResourceLocation STANDARD = PolarisVehicle.id("standard_fuel");

    private static final Set<ResourceLocation> ACCEPTED = Set.of(STANDARD);

    public static boolean isAccepted(ResourceLocation fuelType) {
        return ACCEPTED.contains(fuelType);
    }
}
