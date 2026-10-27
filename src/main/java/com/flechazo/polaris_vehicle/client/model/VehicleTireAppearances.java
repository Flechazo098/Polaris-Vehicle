package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class VehicleTireAppearances {
    private static final String FRONT_LEFT = "1";
    private static final String FRONT_RIGHT = "2";
    private static final String REAR_LEFT = "3";
    private static final String REAR_RIGHT = "4";

    private static final Map<String, Set<VehiclePartSlot>> STANDARD_SLOTS = Map.of(
            FRONT_LEFT, Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
            FRONT_RIGHT, Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
            REAR_LEFT, Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
            REAR_RIGHT, Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL)
    );
    private static final Map<String, Set<VehiclePartSlot>> THREE_WHEEL_SLOTS = Map.of(
            FRONT_LEFT, Set.of(VehiclePartSlot.FRONT_CENTER_WHEEL),
            FRONT_RIGHT, Set.of(),
            REAR_LEFT, Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
            REAR_RIGHT, Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL)
    );

    private static final List<TireStyle> TIRE_STYLES = List.of(
            new TireStyle(VehicleTireDefinitions.ROAD.id(), "road_tire", "road_tire"),
            new TireStyle(
                    VehicleTireDefinitions.ALL_TERRAIN.id(),
                    "all_terrain_tire",
                    "all_terrain_tire"
            ),
            new TireStyle(
                    VehicleTireDefinitions.RUN_FLAT.id(),
                    "run_flat_tire",
                    "run_flat_tire"
            ),
            new TireStyle(
                    VehicleTireDefinitions.RACING_SPEED.id(),
                    "racing_tire",
                    "racing_speed_tire"
            ),
            new TireStyle(
                    VehicleTireDefinitions.RACING_BALANCED.id(),
                    "racing_tire",
                    "racing_balanced_tire"
            ),
            new TireStyle(
                    VehicleTireDefinitions.RACING_ENDURANCE.id(),
                    "racing_tire",
                    "racing_endurance_tire"
            )
    );

    private static final List<VehicleFit> VEHICLE_FITS = List.of(
            fit(VehicleDefinitions.KART_ID, STANDARD_SLOTS, steering(20)),
            fit(VehicleDefinitions.ELDERLY_CAR_ID, STANDARD_SLOTS, steering(20)),
            fit(VehicleDefinitions.SMALL_BUS_ID, STANDARD_SLOTS, steering(15)),
            fit(VehicleDefinitions.SMALL_CAR_ID, THREE_WHEEL_SLOTS, Map.of(FRONT_LEFT, 12.5F)),
            fit(VehicleDefinitions.TWO_SEAT_CAR_ID, STANDARD_SLOTS, steering(20)),
            fit(VehicleDefinitions.SUV_ID, STANDARD_SLOTS, steering(17.5F)),
            fit(VehicleDefinitions.CONVERTIBLE_ID, STANDARD_SLOTS, steering(20)),
            fit(VehicleDefinitions.RALLY_CAR_ID, STANDARD_SLOTS, steering(15)),
            fit(VehicleDefinitions.FORMULA_CAR_ID, STANDARD_SLOTS, steering(28))
    );

    public static final List<VehicleTireAppearance> ALL = buildAppearances();
    private static final Map<Key, VehicleTireAppearance> BY_KEY = index(ALL);

    public static Maybe<VehicleTireAppearance> find(
            ResourceLocation vehicleType,
            ResourceLocation tireType
    ) {
        return Maybe.ofNullable(BY_KEY.get(new Key(vehicleType, tireType)));
    }

    private static List<VehicleTireAppearance> buildAppearances() {
        return Pathway.listPath(VEHICLE_FITS)
                .via(fit -> Pathway.listPath(TIRE_STYLES).map(style -> new VehicleTireAppearance(
                        fit.vehicleType(),
                        style.tireType(),
                        PolarisVehicle.id("meshes/vehicle/tires/" + fit.vehicleType().getPath() + "/"
                                + style.modelName() + ".geo.json"),
                        PolarisVehicle.id("textures/entity/vehicle/tires/formula_car/"
                                + style.textureName() + ".png"),
                        fit.boneSlots(),
                        fit.steeringBones(),
                        fit.poses()
                )))
                .run();
    }

    private static Map<Key, VehicleTireAppearance> index(
            List<VehicleTireAppearance> all
    ) {
        Map<Key, VehicleTireAppearance> appearances = new LinkedHashMap<>();
        for (VehicleTireAppearance appearance : all) {
            Key key = new Key(appearance.vehicleType(), appearance.tireType());
            if (appearances.putIfAbsent(key, appearance) != null) {
                throw new IllegalStateException(
                        "Duplicate vehicle tire appearance for "
                                + appearance.vehicleType() + " + " + appearance.tireType()
                );
            }
        }
        return Map.copyOf(appearances);
    }

    private static VehicleFit fit(ResourceLocation vehicleType, Map<String, Set<VehiclePartSlot>> slots,
                                  Map<String, Float> steering) {
        Map<String, VehicleTireAppearance.BonePose> poses = new LinkedHashMap<>();
        slots.keySet().forEach(bone -> poses.put(bone, VehicleTireAppearance.BonePose.uniform(0, 0, 0, 1)));
        return new VehicleFit(vehicleType, slots, steering, Map.copyOf(poses));
    }

    private static Map<String, Float> steering(float lockAngle) {
        return Map.of(FRONT_LEFT, lockAngle, FRONT_RIGHT, lockAngle);
    }

    private record TireStyle(
            ResourceLocation tireType,
            String modelName,
            String textureName
    ) {
    }

    private record VehicleFit(ResourceLocation vehicleType,
                              Map<String, Set<VehiclePartSlot>> boneSlots,
                              Map<String, Float> steeringBones,
                              Map<String, VehicleTireAppearance.BonePose> poses) {
    }

    private record Key(ResourceLocation vehicleType, ResourceLocation tireType) {
    }
}
