package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VehiclePaintSkins {
    public record Assets(ResourceLocation texture, ResourceLocation chassis, ResourceLocation control) {
    }

    private record Registration(VehiclePaintDefinition paint, Map<ResourceLocation, Assets> vehicles) {
    }

    private static final Map<ResourceLocation, Registration> REGISTRATIONS = new ConcurrentHashMap<>();

    public static void register(VehiclePaintDefinition paint, Map<ResourceLocation, Assets> vehicles) {
        var assets = Map.copyOf(vehicles);
        if (assets.isEmpty() || !VehicleDefinitions.ALL.keySet().containsAll(assets.keySet())) {
            throw new IllegalArgumentException("A livery must name supported registered vehicles: " + paint.id());
        }
        if (VehiclePaintDefinitions.ALL.containsKey(paint.id())
                || REGISTRATIONS.putIfAbsent(paint.id(), new Registration(paint, assets)) != null) {
            throw new IllegalArgumentException("Duplicate vehicle paint: " + paint.id());
        }
    }

    public static Maybe<VehiclePaintDefinition> definition(ResourceLocation paintId) {
        return Maybe.ofNullable(paintId)
                .flatMap(id -> Maybe.ofNullable(REGISTRATIONS.get(id)))
                .map(Registration::paint);
    }

    public static Maybe<Assets> assets(ResourceLocation vehicleType, ResourceLocation paintId) {
        return Maybe.ofNullable(paintId)
                .flatMap(id -> Maybe.ofNullable(REGISTRATIONS.get(id)))
                .flatMap(registration -> Maybe.ofNullable(registration.vehicles().get(vehicleType)));
    }
}
