package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VehicleAssets {
    public record SteeringControl(String bone) {
        public SteeringControl {
            if (bone == null || bone.isBlank()) {
                throw new IllegalArgumentException("Steering control bone may not be blank");
            }
        }
    }

    private static final Map<String, ResourceLocation> VEHICLE_TYPE_BY_MODEL = Map.of(
            "kdc2", VehicleDefinitions.KART_ID,
            "ltl_car", VehicleDefinitions.ELDERLY_CAR_ID,
            "bus", VehicleDefinitions.SMALL_BUS_ID,
            "slc_ltl", VehicleDefinitions.SMALL_CAR_ID,
            "ltl_car2", VehicleDefinitions.TWO_SEAT_CAR_ID,
            "suv", VehicleDefinitions.SUV_ID,
            "mini_car", VehicleDefinitions.CONVERTIBLE_ID,
            "f1", VehicleDefinitions.FORMULA_CAR_ID,
            "racing", VehicleDefinitions.RALLY_CAR_ID
    );
    private static final Map<ResourceLocation, List<String>> WHEEL_BONES = Map.of(
            VehicleDefinitions.KART_ID,
            List.of("rightTire", "rightTire2", "leftTire2", "leftTire"),
            VehicleDefinitions.ELDERLY_CAR_ID,
            List.of("leftTire1", "leftTire2", "rightTire2", "rightTire1"),
            VehicleDefinitions.SMALL_BUS_ID,
            List.of("leftTire2", "rightTire2", "rightTire1", "leftTire1"),
            VehicleDefinitions.SMALL_CAR_ID,
            List.of("leftTire2", "leftTire3", "rightTire2"),
            VehicleDefinitions.TWO_SEAT_CAR_ID,
            List.of("leftTire1", "leftTire2", "rightTire2", "rightTire1"),
            VehicleDefinitions.SUV_ID,
            List.of("leftTire1", "leftTire2", "rightTire2", "rightTire1"),
            VehicleDefinitions.CONVERTIBLE_ID,
            List.of("tire3", "tire4", "tire2", "tire5"),
            VehicleDefinitions.FORMULA_CAR_ID,
            List.of("rightTire-1", "leftTire-1", "leftTire-2", "rightTire-2"),
            VehicleDefinitions.RALLY_CAR_ID, List.of("tire1", "tire2", "tire3", "tire4")
    );
    private static final Map<ResourceLocation, Map<String, Float>> STEERING_BONES = Map.of(
            VehicleDefinitions.KART_ID, Map.of(
                    "rightTire", 20.0F,
                    "leftTire", 20.0F
            ),
            VehicleDefinitions.ELDERLY_CAR_ID, Map.of(
                    "leftTire1", 20.0F,
                    "rightTire1", 20.0F
            ),
            VehicleDefinitions.SMALL_BUS_ID, Map.of(
                    "leftTire2", 15.0F,
                    "rightTire2", 15.0F
            ),
            VehicleDefinitions.SMALL_CAR_ID, Map.of(
                    "leftTire3", 12.5F
            ),
            VehicleDefinitions.TWO_SEAT_CAR_ID, Map.of(
                    "leftTire1", 20.0F,
                    "rightTire1", 20.0F
            ),
            VehicleDefinitions.SUV_ID, Map.of(
                    "leftTire1", 17.5F,
                    "rightTire1", 17.5F
            ),
            VehicleDefinitions.CONVERTIBLE_ID, Map.of(
                    "tire3", 20.0F,
                    "tire4", 20.0F
            ),
            VehicleDefinitions.FORMULA_CAR_ID, Map.of(
                    "rightTire-1", 28.0F,
                    "leftTire-1", 28.0F
            ),
            VehicleDefinitions.RALLY_CAR_ID, Map.of("tire1", 15.0F, "tire2", 15.0F)
    );
    private static final Map<ResourceLocation, SteeringControl> STEERING_CONTROLS = Map.of(
            VehicleDefinitions.KART_ID, new SteeringControl("wheelMainPart"),
            VehicleDefinitions.ELDERLY_CAR_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.SMALL_BUS_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.SMALL_CAR_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.TWO_SEAT_CAR_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.SUV_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.CONVERTIBLE_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.FORMULA_CAR_ID, new SteeringControl("wheelMain"),
            VehicleDefinitions.RALLY_CAR_ID, new SteeringControl("wheelMain")
    );
    private static final Map<ResourceLocation, Map<String, Set<VehiclePartSlot>>>
            WHEEL_BONE_SLOTS = Map.of(
            VehicleDefinitions.KART_ID, Map.of(
                    "rightTire", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
                    "rightTire2", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL),
                    "leftTire2", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
                    "leftTire", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL)
            ),
            VehicleDefinitions.ELDERLY_CAR_ID, standardNamedWheelSlots(),
            VehicleDefinitions.SMALL_BUS_ID, Map.of(
                    "leftTire2", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
                    "rightTire2", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
                    "rightTire1", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL),
                    "leftTire1", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL)
            ),
            VehicleDefinitions.SMALL_CAR_ID, Map.of(
                    "leftTire3", Set.of(VehiclePartSlot.FRONT_CENTER_WHEEL),
                    "leftTire2", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
                    "rightTire2", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL)
            ),
            VehicleDefinitions.TWO_SEAT_CAR_ID, standardNamedWheelSlots(),
            VehicleDefinitions.SUV_ID, standardNamedWheelSlots(),
            VehicleDefinitions.CONVERTIBLE_ID, Map.of(
                    "tire3", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
                    "tire4", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
                    "tire2", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
                    "tire5", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL)
            ),
            VehicleDefinitions.FORMULA_CAR_ID, Map.of(
                    "rightTire-1", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
                    "leftTire-1", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
                    "leftTire-2", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
                    "rightTire-2", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL)
            ),
            VehicleDefinitions.RALLY_CAR_ID, Map.of(
                    "tire1", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
                    "tire2", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL),
                    "tire3", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL),
                    "tire4", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL)
            )
    );

    public static String modelName(ResourceLocation vehicleType) {
        if (VehicleDefinitions.KART_ID.equals(vehicleType)) {
            return "kdc2";
        }
        if (VehicleDefinitions.ELDERLY_CAR_ID.equals(vehicleType)) {
            return "ltl_car";
        }
        if (VehicleDefinitions.SMALL_BUS_ID.equals(vehicleType)) {
            return "bus";
        }
        if (VehicleDefinitions.SMALL_CAR_ID.equals(vehicleType)) {
            return "slc_ltl";
        }
        if (VehicleDefinitions.TWO_SEAT_CAR_ID.equals(vehicleType)) {
            return "ltl_car2";
        }
        if (VehicleDefinitions.SUV_ID.equals(vehicleType)) {
            return "suv";
        }
        if (VehicleDefinitions.CONVERTIBLE_ID.equals(vehicleType)) {
            return "mini_car";
        }
        if (VehicleDefinitions.FORMULA_CAR_ID.equals(vehicleType)) {
            return "f1";
        }
        if (VehicleDefinitions.RALLY_CAR_ID.equals(vehicleType)) {
            return "racing";
        }
        throw new IllegalArgumentException("Unknown vehicle asset mapping: " + vehicleType);
    }

    public static String chassisModelName(ResourceLocation vehicleType) {
        return modelName(vehicleType) + "_shelf";
    }

    public static ResourceLocation firstPersonDriverGeometry(ResourceLocation vehicleType) {
        return PolarisVehicle.id("geo/entity/driver/" + modelName(vehicleType) + ".geo.json");
    }

    public static ResourceLocation firstPersonDriverAnimation(ResourceLocation vehicleType) {
        return PolarisVehicle.id("animations/entity/driver/" + modelName(vehicleType) + ".animation.json");
    }

    public static String tireModelName(ResourceLocation vehicleType) {
        return VehicleDefinitions.SUV_ID.equals(vehicleType)
                ? "suv_wheel"
                : modelName(vehicleType) + "_tire";
    }

    public static String controlModelName(ResourceLocation vehicleType) {
        if (VehicleDefinitions.SUV_ID.equals(vehicleType)) {
            return "suv_tire";
        }
        return VehicleDefinitions.TWO_SEAT_CAR_ID.equals(vehicleType)
                ? "ltl_car2_wheel2"
                : modelName(vehicleType) + "_wheel";
    }

    public static String textureName(ResourceLocation vehicleType) {
        return VehicleDefinitions.SMALL_CAR_ID.equals(vehicleType)
                ? "lel_car3"
                : modelName(vehicleType);
    }

    public static String textureName(ResourceLocation vehicleType, ResourceLocation paintId) {
        String baseName = textureName(vehicleType);
        return VehiclePaintDefinitions.find(paintId)
                .fold(() -> baseName, paint -> baseName + paint.textureSuffix());
    }

    public static ResourceLocation paintTexture(ResourceLocation vehicleType, ResourceLocation paintId) {
        return VehiclePaintSkins.assets(vehicleType, paintId).flatMap(assets -> Maybe.ofNullable(assets.texture()))
                .orElseGet(() -> PolarisVehicle.id("textures/entity/vehicle/" + textureName(vehicleType, paintId) + ".png"));
    }

    public static ResourceLocation paintChassis(ResourceLocation vehicleType, ResourceLocation paintId) {
        return VehiclePaintSkins.assets(vehicleType, paintId).flatMap(assets -> Maybe.ofNullable(assets.chassis()))
                .orElseGet(() -> PolarisVehicle.id("geo/vehicle/" + chassisModelName(vehicleType) + ".geo.json"));
    }

    public static ResourceLocation paintControl(ResourceLocation vehicleType, ResourceLocation paintId) {
        return VehiclePaintSkins.assets(vehicleType, paintId).flatMap(assets -> Maybe.ofNullable(assets.control()))
                .orElseGet(() -> PolarisVehicle.id("geo/vehicle/" + controlModelName(vehicleType) + ".geo.json"));
    }

    public static Maybe<ResourceLocation> vehicleTypeForModelName(String modelName) {
        if (!modelName.endsWith("_shelf")) {
            return Maybe.none();
        }
        return Maybe.ofNullable(VEHICLE_TYPE_BY_MODEL.get(
                modelName.substring(0, modelName.length() - "_shelf".length())
        ));
    }

    public static List<String> wheelBones(ResourceLocation vehicleType) {
        return Maybe.ofNullable(WHEEL_BONES.get(vehicleType)).fold(
                () -> {
                    throw new IllegalArgumentException("Unknown wheel animation mapping: " + vehicleType);
                },
                bones -> bones
        );
    }

    public static Map<String, Float> steeringBones(ResourceLocation vehicleType) {
        return Maybe.ofNullable(STEERING_BONES.get(vehicleType)).fold(
                () -> {
                    throw new IllegalArgumentException("Unknown steering animation mapping: " + vehicleType);
                },
                bones -> bones
        );
    }

    public static String steeringAnimationName(ResourceLocation vehicleType, boolean left) {
        String prefix = VehicleDefinitions.FORMULA_CAR_ID.equals(vehicleType)
                || VehicleDefinitions.SUV_ID.equals(vehicleType) ? "unknown" : modelName(vehicleType);
        boolean exportedLeft = VehicleDefinitions.RALLY_CAR_ID.equals(vehicleType) == left;
        return "animation." + prefix + (exportedLeft ? ".turn_left" : ".turn_right");
    }

    public static float authoredSteeringInput(ResourceLocation vehicleType, float steering) {
        return VehicleDefinitions.RALLY_CAR_ID.equals(vehicleType) ? steering : -steering;
    }

    public static SteeringControl steeringControl(ResourceLocation vehicleType) {
        return Maybe.ofNullable(STEERING_CONTROLS.get(vehicleType)).fold(
                () -> {
                    throw new IllegalArgumentException(
                            "Unknown steering-control mapping: " + vehicleType);
                },
                control -> control
        );
    }

    public static Map<String, Set<VehiclePartSlot>> wheelBoneSlots(ResourceLocation vehicleType) {
        return Maybe.ofNullable(WHEEL_BONE_SLOTS.get(vehicleType)).fold(
                () -> {
                    throw new IllegalArgumentException(
                            "Unknown wheel visibility mapping: " + vehicleType);
                },
                slots -> slots
        );
    }

    private static Map<String, Set<VehiclePartSlot>> standardNamedWheelSlots() {
        return Map.of(
                "leftTire1", Set.of(VehiclePartSlot.FRONT_LEFT_WHEEL),
                "leftTire2", Set.of(VehiclePartSlot.REAR_LEFT_WHEEL),
                "rightTire2", Set.of(VehiclePartSlot.REAR_RIGHT_WHEEL),
                "rightTire1", Set.of(VehiclePartSlot.FRONT_RIGHT_WHEEL)
        );
    }
}
