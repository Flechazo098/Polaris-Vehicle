package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physical wheel placement read from the same fitted tire meshes the renderer draws.
 *
 * <p>Each vehicle has one mesh per tire style, with the artist's placement and scale
 * already baked in. A wheel's centre is the centre of its mesh bounds and its rolling
 * radius is half the mesh height, so the physical contact patch is exactly the
 * visible tread bottom. Bedrock model space has its front at -Z; physics uses +Z.</p>
 *
 * <p>The meshes ship inside the mod jar, so the logical server reads them from the
 * classpath rather than from a client resource manager.</p>
 */
public final class VehicleTireFit {
    /**
     * Mesh used for suspension mounts and the no-tire fallback radius.
     */
    public static final String REFERENCE_STYLE = "road_tire";
    private static final String MESH_ROOT = "/assets/polaris_vehicle/meshes/vehicle/tires/";
    private static final double PIXELS_PER_METER = 16.0;
    private static final Map<String, VehiclePartSlot> STANDARD_BONES = Map.of(
            "1", VehiclePartSlot.FRONT_LEFT_WHEEL,
            "2", VehiclePartSlot.FRONT_RIGHT_WHEEL,
            "3", VehiclePartSlot.REAR_LEFT_WHEEL,
            "4", VehiclePartSlot.REAR_RIGHT_WHEEL
    );
    private static final Map<String, VehiclePartSlot> THREE_WHEEL_BONES = Map.of(
            "1", VehiclePartSlot.FRONT_CENTER_WHEEL,
            "3", VehiclePartSlot.REAR_LEFT_WHEEL,
            "4", VehiclePartSlot.REAR_RIGHT_WHEEL
    );
    private static final Map<Key, Map<VehiclePartSlot, Wheel>> CACHE = new ConcurrentHashMap<>();

    /**
     * Physics-space wheel centre (metres, +Z front) and tread radius (metres).
     */
    public record Wheel(Vec3 center, double radius) {
    }

    private record Key(ResourceLocation vehicleType, String style) {
    }

    private VehicleTireFit() {
    }

    /**
     * Fitted mesh name for a registered tire; all racing tires share one tread.
     */
    public static String styleFor(ResourceLocation tireId) {
        String path = tireId.getPath();
        if (VehicleTireDefinitions.RACING_SPEED.id().equals(tireId)
                || VehicleTireDefinitions.RACING_BALANCED.id().equals(tireId)
                || VehicleTireDefinitions.RACING_ENDURANCE.id().equals(tireId)) {
            return "racing_tire";
        }
        return path;
    }

    public static Map<VehiclePartSlot, Wheel> wheels(ResourceLocation vehicleType, String style) {
        return CACHE.computeIfAbsent(new Key(vehicleType, style), VehicleTireFit::load);
    }

    public static Wheel wheel(ResourceLocation vehicleType, String style, VehiclePartSlot slot) {
        Wheel wheel = wheels(vehicleType, style).get(slot);
        if (wheel == null) {
            throw new IllegalStateException("Tire mesh " + style + " for " + vehicleType + " has no " + slot);
        }
        return wheel;
    }

    private static Map<VehiclePartSlot, Wheel> load(Key key) {
        String path = MESH_ROOT + key.vehicleType().getPath() + "/" + key.style() + ".geo.json";
        JsonObject document;
        try (var stream = VehicleTireFit.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing fitted tire mesh " + path);
            document = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fitted tire mesh " + path, exception);
        }
        Map<String, VehiclePartSlot> bones = key.vehicleType().getPath().equals("small_car")
                ? THREE_WHEEL_BONES : STANDARD_BONES;
        Map<VehiclePartSlot, Wheel> wheels = new EnumMap<>(VehiclePartSlot.class);
        for (var boneElement : document.getAsJsonArray("minecraft:geometry").get(0)
                .getAsJsonObject().getAsJsonArray("bones")) {
            JsonObject bone = boneElement.getAsJsonObject();
            VehiclePartSlot slot = bones.get(bone.get("name").getAsString());
            if (slot == null || !bone.has("poly_mesh")) continue;
            JsonArray positions = bone.getAsJsonObject("poly_mesh").getAsJsonArray("positions");
            double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (var element : positions) {
                JsonArray position = element.getAsJsonArray();
                for (int axis = 0; axis < 3; axis++) {
                    double value = position.get(axis).getAsDouble();
                    min[axis] = Math.min(min[axis], value);
                    max[axis] = Math.max(max[axis], value);
                }
            }
            if (positions.isEmpty()) throw new IllegalStateException("Empty tire bone " + slot + " in " + path);
            Vec3 center = new Vec3(
                    (min[0] + max[0]) * 0.5 / PIXELS_PER_METER,
                    (min[1] + max[1]) * 0.5 / PIXELS_PER_METER,
                    -(min[2] + max[2]) * 0.5 / PIXELS_PER_METER
            );
            wheels.put(slot, new Wheel(center, (max[1] - min[1]) * 0.5 / PIXELS_PER_METER));
        }
        if (wheels.size() != (bones == THREE_WHEEL_BONES ? 3 : 4)) {
            throw new IllegalStateException("Tire mesh " + path + " does not define every wheel: " + wheels.keySet());
        }
        return Map.copyOf(wheels);
    }
}
