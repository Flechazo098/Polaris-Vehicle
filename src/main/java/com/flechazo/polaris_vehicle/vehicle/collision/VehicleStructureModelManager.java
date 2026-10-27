package com.flechazo.polaris_vehicle.vehicle.collision;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureDocument;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class VehicleStructureModelManager
        extends SimplePreparableReloadListener<Map<ResourceLocation, VehicleCollisionModel>> {
    public static final VehicleStructureModelManager SERVER = new VehicleStructureModelManager(
            FileToIdConverter.json("vehicle_structures"),
            Maybe::some
    );
    public static final VehicleStructureModelManager CLIENT = new VehicleStructureModelManager(
            new FileToIdConverter("geo/vehicle", ".geo.json"),
            modelId -> PolarisVehicle.MODID.equals(modelId.getNamespace())
                    ? VehicleAssets.vehicleTypeForModelName(modelId.getPath())
                    : Maybe.none()
    );

    private final FileToIdConverter converter;
    private final Function<ResourceLocation, Maybe<ResourceLocation>> idResolver;
    private volatile Map<ResourceLocation, VehicleCollisionModel> models = Map.of();

    VehicleStructureModelManager(
            FileToIdConverter converter,
            Function<ResourceLocation, Maybe<ResourceLocation>> idResolver
    ) {
        this.converter = converter;
        this.idResolver = idResolver;
    }

    public static void registerServerListener() {
        NeoForge.EVENT_BUS.addListener(VehicleStructureModelManager::addServerListener);
    }

    public static VehicleStructureModelManager forLevel(Level level) {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    public Maybe<VehicleCollisionModel> find(ResourceLocation vehicleType) {
        return Maybe.ofNullable(models.get(vehicleType));
    }

    public Map<ResourceLocation, VehicleCollisionModel> models() {
        return models;
    }

    @Override
    protected Map<ResourceLocation, VehicleCollisionModel> prepare(
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        Map<ResourceLocation, VehicleCollisionModel> prepared = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry
                : converter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation modelId = converter.fileToId(fileId);
            Pathway.maybe(idResolver.apply(modelId)).peek(vehicleType -> {
                try (Reader reader = entry.getValue().openAsReader()) {
                    BedrockStructureDocument document = CodecUtil.readJson(BedrockStructureDocument.CODEC, reader);
                    if (prepared.containsKey(vehicleType)) {
                        throw new IllegalStateException(
                                "Duplicate vehicle structure id " + vehicleType
                        );
                    }
                    prepared.put(vehicleType, VehicleCollisionModel.compile(document));
                } catch (Exception ignored) {
                }
            });
        }
        return Map.copyOf(prepared);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, VehicleCollisionModel> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        models = prepared;

    }

    private static void addServerListener(AddReloadListenerEvent event) {
        event.addListener(SERVER);
    }
}
