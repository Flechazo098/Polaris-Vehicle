package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.polaris_vehicle.part.*;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class VehiclePartCompatibility {
    private VehiclePartCompatibility() {
    }

    public static List<ResourceLocation> compatiblePartIds(
            VehicleDefinition vehicle,
            VehiclePartSlot slot
    ) {
        List<ResourceLocation> result = new ArrayList<>();
        VehicleTireDefinitions.ALL.values().stream()
                .filter(part -> vehicle.supports(slot, VehiclePartCategory.WHEEL))
                .map(VehicleTireDefinition::id)
                .forEach(result::add);
        VehicleEngineDefinitions.ALL.values().stream()
                .filter(part -> vehicle.supports(slot, part.category()))
                .map(VehicleEngineDefinition::id)
                .forEach(result::add);
        VehiclePowerDefinitions.ALL.values().stream()
                .filter(part -> vehicle.supports(slot, part.category()))
                .map(VehiclePowerDefinition::id)
                .forEach(result::add);
        return List.copyOf(result);
    }
}
