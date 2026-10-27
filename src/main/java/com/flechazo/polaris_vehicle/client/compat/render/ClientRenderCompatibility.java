package com.flechazo.polaris_vehicle.client.compat.render;

import net.neoforged.fml.ModList;

import java.util.Set;

public interface ClientRenderCompatibility {
    String id();

    Set<String> requiredMods();

    default boolean isAvailable(ModList mods) {
        return requiredMods().stream().allMatch(mods::isLoaded);
    }
}
