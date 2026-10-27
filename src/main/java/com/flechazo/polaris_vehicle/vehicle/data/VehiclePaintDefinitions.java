package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public final class VehiclePaintDefinitions {
    public static final VehiclePaintDefinition BLACK = paint("black");
    public static final VehiclePaintDefinition BLUE = paint("blue");
    public static final VehiclePaintDefinition PINK = paint("pink");
    public static final VehiclePaintDefinition GREEN = paint("green");
    public static final VehiclePaintDefinition RED = paint("red");
    public static final VehiclePaintDefinition WHITE = paint("white");
    public static final VehiclePaintDefinition BREAD = paint("bread");

    public static final List<VehiclePaintDefinition> ORDER = List.of(
            WHITE, BLACK, BLUE, PINK, GREEN, RED, BREAD
    );

    public static final Map<ResourceLocation, VehiclePaintDefinition> ALL = Map.of(
            BLACK.id(), BLACK,
            BLUE.id(), BLUE,
            PINK.id(), PINK,
            GREEN.id(), GREEN,
            RED.id(), RED,
            WHITE.id(), WHITE,
            BREAD.id(), BREAD
    );

    public static Maybe<Integer> number(VehiclePaintDefinition paint) {
        int index = ORDER.indexOf(paint);
        return index < 0 || paint.equals(BREAD) ? Maybe.none() : Maybe.some(index + 1);
    }

    public static Maybe<VehiclePaintDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id).flatMap(validId -> Maybe.ofNullable(ALL.get(validId))
                .or(() -> VehiclePaintSkins.definition(validId)));
    }

    public static boolean supports(ResourceLocation vehicleType, ResourceLocation paintId) {
        return Pathway.maybe(VehicleDefinitions.find(vehicleType))
                .map(vehicle -> VehicleInstanceData.DEFAULT_PAINT.equals(paintId)
                        || VehiclePaintSkins.definition(paintId).fold(
                        () -> find(paintId).filter(paint -> !BREAD.id().equals(paint.id())
                                || VehicleDefinitions.CONVERTIBLE_ID.equals(vehicle.id())).isDefined(),
                        paint -> VehiclePaintSkins.assets(vehicle.id(), paint.id()).isDefined()))
                .getOrElse(false);
    }

    private static VehiclePaintDefinition paint(String name) {
        return new VehiclePaintDefinition(
                PolarisVehicle.id(name),
                "_" + name
        );
    }
}
