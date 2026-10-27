package com.flechazo.polaris_vehicle.client.compat.render;

import com.flechazo.hkt.business.core.Pathway;
import net.neoforged.fml.ModList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientRenderCompatRegistry {
    public static final String SODIUM = "sodium";
    public static final String IRIS_SODIUM = "iris_sodium";
    public static final String ACCELERATED_RENDERING = "acceleratedrendering";

    private static final List<ClientRenderCompatibility> MODULES = List.of(
            createCompatibility(SODIUM, "sodium"),
            createCompatibility(IRIS_SODIUM, "iris", "sodium"),
            createCompatibility(ACCELERATED_RENDERING, "acceleratedrendering")
    );
    private static volatile Map<String, Boolean> availability = Map.of();

    public static void initialize() {
        ModList mods = ModList.get();
        availability = Map.copyOf(Pathway.listPath(MODULES)
                .foldLeft(new LinkedHashMap<String, Boolean>(), (detected, compatibility) -> {
                    boolean active = compatibility.isAvailable(mods);
                    detected.put(compatibility.id(), active);
                    return detected;
                }));
    }

    public static boolean isActive(String id) {
        Boolean cached = availability.get(id);
        if (cached != null) {
            return cached;
        }
        ModList mods = ModList.get();
        for (var compatibility : MODULES) {
            if (compatibility.id().equals(id)) return compatibility.isAvailable(mods);
        }
        return false;
    }

    private static ClientRenderCompatibility createCompatibility(String id, String... requiredMods) {
        Set<String> requirements = Set.of(requiredMods);
        return new ClientRenderCompatibility() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> requiredMods() {
                return requirements;
            }
        };
    }
}
