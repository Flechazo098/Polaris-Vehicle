package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

public final class VehicleModel<T extends AbstractVehicleEntity> extends GeoModel<T> {
    private record CachedMesh(BakedGeoModel source, BakedGeoModel[] levels) {
    }

    private final Map<ResourceLocation, CachedMesh> meshes = new HashMap<>();
    private BakedGeoModel activeMesh;
    private int lod;

    public int lod() {
        return lod;
    }

    public void setLod(int lod) {
        this.lod = Math.clamp(lod, 0, 2);
    }

    @Override
    public BakedGeoModel getBakedModel(ResourceLocation resource) {
        var source = GeckoLibCache.getBakedModels().get(resource);
        if (source == null) return super.getBakedModel(resource);
        var entry = meshes.get(resource);
        if (entry == null || entry.source() != source) {
            entry = new CachedMesh(source, new BakedGeoModel[3]);
            meshes.put(resource, entry);
        }
        var selected = entry.levels()[lod];
        if (selected == null) {
            selected = StaticVehicleMesh.bake(source, lod);
            entry.levels()[lod] = selected;
        }
        if (activeMesh != selected) {
            activeMesh = selected;
            getAnimationProcessor().setActiveModel(selected);
        }
        return selected;
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return VehicleAssets.paintChassis(animatable.vehicleTypeId(), animatable.vehicleData().paintId());
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return VehicleAssets.paintTexture(animatable.vehicleTypeId(), animatable.vehicleData().paintId());
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return PolarisVehicle.id("animations/vehicle/"
                + VehicleAssets.modelName(animatable.vehicleTypeId())
                + ".animation.json");
    }

}
