package com.flechazo.polaris_vehicle.part;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VehicleRepairRecipeManager
        extends SimplePreparableReloadListener<Map<ResourceLocation, VehicleRepairRecipeDefinition>> {
    public static final VehicleRepairRecipeManager INSTANCE = new VehicleRepairRecipeManager();
    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("vehicle_repair_recipes");
    private volatile Map<ResourceLocation, VehicleRepairRecipeDefinition> recipes = Map.of();

    public static void registerServerListener() {
        NeoForge.EVENT_BUS.addListener(VehicleRepairRecipeManager::addReloadListener);
    }

    public Maybe<VehicleRepairRecipeDefinition> find(InstalledPart part, ItemStack material) {
        for (var recipe : recipes.values()) {
            if (recipe.matches(part, material)) return Maybe.some(recipe);
        }
        return Maybe.none();
    }

    public Maybe<VehicleRepairRecipeDefinition> find(ResourceLocation id) {
        return Maybe.ofNullable(id).flatMap(validId -> Maybe.ofNullable(recipes.get(validId)));
    }

    @Override
    protected Map<ResourceLocation, VehicleRepairRecipeDefinition> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, VehicleRepairRecipeDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : CONVERTER.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation expectedId = CONVERTER.fileToId(fileId);
            try (Reader reader = entry.getValue().openAsReader()) {
                VehicleRepairRecipeDefinition recipe = CodecUtil.readJson(VehicleRepairRecipeDefinition.CODEC, reader);
                if (!expectedId.equals(recipe.id())) {
                    throw new IllegalArgumentException(
                            "Repair recipe id " + recipe.id() + " does not match file id " + expectedId
                    );
                }
                if (loaded.putIfAbsent(recipe.id(), recipe) != null) {
                    throw new IllegalStateException("Duplicate vehicle repair recipe " + recipe.id());
                }
            } catch (Exception ignored) {

            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, VehicleRepairRecipeDefinition> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        recipes = prepared;

    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }
}
