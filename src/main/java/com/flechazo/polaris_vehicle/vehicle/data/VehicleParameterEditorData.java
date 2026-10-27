package com.flechazo.polaris_vehicle.vehicle.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VehicleParameterEditorData {

    public static JsonElement defaults(String kind, ResourceLocation id) {
        requireKnown(kind, id);
        return kind.equals("tire")
                ? VehicleTireParameters.CODEC.encodeStart(JsonOps.INSTANCE, VehicleTireParameters.defaultsView().get(id)).getOrThrow()
                : VehicleTypeParameters.CODEC.encodeStart(JsonOps.INSTANCE, VehicleTypeParameters.defaultsView().get(id)).getOrThrow();
    }

    public static Map<String, JsonElement> updates(String kind, ResourceLocation id, String json, String action) {
        requireKnown(kind, id);
        var values = new LinkedHashMap<String, JsonElement>();
        switch (action) {
            case "reset_all" -> {
                VehicleTypeParameters.defaultsView().keySet().forEach(key -> values.put(path("vehicle", key), defaults("vehicle", key)));
                VehicleTireParameters.defaultsView().keySet().forEach(key -> values.put(path("tire", key), defaults("tire", key)));
            }
            case "reset" -> values.put(path(kind, id), defaults(kind, id));
            case "save" -> {
                var candidate = JsonParser.parseString(json);
                JsonElement canonical = kind.equals("tire")
                        ? VehicleTireParameters.CODEC.encodeStart(JsonOps.INSTANCE,
                        VehicleTireParameters.CODEC.parse(JsonOps.INSTANCE, candidate).getOrThrow()).getOrThrow()
                        : VehicleTypeParameters.CODEC.encodeStart(JsonOps.INSTANCE,
                        VehicleTypeParameters.CODEC.parse(JsonOps.INSTANCE, candidate).getOrThrow()).getOrThrow();
                values.put(path(kind, id), canonical);
            }
            default -> throw new IllegalArgumentException("Unknown editor action " + action);
        }
        return Collections.unmodifiableMap(values);
    }

    private static void requireKnown(String kind, ResourceLocation id) {
        boolean known = switch (kind) {
            case "vehicle" -> VehicleTypeParameters.defaultsView().containsKey(id);
            case "tire" -> VehicleTireParameters.defaultsView().containsKey(id);
            default -> false;
        };
        if (!known) throw new IllegalArgumentException("Unknown parameter entry " + kind + ":" + id);
    }

    private static String path(String kind, ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + (kind.equals("tire") ? "tire_parameters" : "vehicle_parameters")
                + "/" + id.getPath() + ".json";
    }
}
