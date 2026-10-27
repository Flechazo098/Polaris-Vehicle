package com.flechazo.polaris_vehicle.client.model;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.GeckoLibConstants;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Gives each model owner its own mutable bone tree, while sharing baked geometry.
 * GeckoLib's resource cache shares bones as well as geometry. Item display selection,
 * vehicle fitting and animation therefore otherwise overwrite each other's poses,
 * including during passes where GeckoLib skips an already-ticked animation callback.
 */
public abstract class IsolatedGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Map<ResourceLocation, OwnedModel> models = new HashMap<>();
    private BakedGeoModel activeModel;

    @Override
    public final BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel source = loadSharedModel(location);
        OwnedModel owned = models.get(location);
        // Resource reload replaces the globally baked model; replace the owned copy too.
        if (owned == null || owned.source() != source) {
            owned = new OwnedModel(source, copyRestModel(source));
            models.put(location, owned);
        }
        if (activeModel != owned.model()) {
            activeModel = owned.model();
            getAnimationProcessor().setActiveModel(activeModel);
        }
        return activeModel;
    }

    protected BakedGeoModel loadSharedModel(ResourceLocation location) {
        BakedGeoModel source = GeckoLibCache.getBakedModels().get(location);
        if (source == null) {
            if (!location.getPath().contains("geo/")) {
                throw GeckoLibConstants.exception(location,
                        "Invalid model resource path provided - GeckoLib models must be placed in assets/<modid>/geo/");
            }
            throw GeckoLibConstants.exception(location, "Unable to find model");
        }
        return source;
    }

    private static BakedGeoModel copyRestModel(BakedGeoModel source) {
        var roots = new ArrayList<GeoBone>(source.topLevelBones().size());
        for (GeoBone root : source.topLevelBones()) {
            roots.add(copyRestBone(root, null));
        }
        return new BakedGeoModel(roots, source.properties());
    }

    private static GeoBone copyRestBone(GeoBone source, GeoBone parent) {
        GeoBone copy = new GeoBone(parent, source.getName(), source.getMirror(),
                source.getInflate(), source.shouldNeverRender(), source.getReset());
        copy.updatePivot(source.getPivotX(), source.getPivotY(), source.getPivotZ());
        BoneSnapshot rest = source.getInitialSnapshot();
        if (rest == null) {
            rest = source.saveSnapshot();
        }
        copy.updateRotation(rest.getRotX(), rest.getRotY(), rest.getRotZ());
        copy.updatePosition(rest.getOffsetX(), rest.getOffsetY(), rest.getOffsetZ());
        copy.updateScale(rest.getScaleX(), rest.getScaleY(), rest.getScaleZ());
        copy.saveInitialSnapshot();
        copy.resetStateChanges();
        // Renderers read these authored cubes/quads; only bone state needs isolation.
        copy.getCubes().addAll(source.getCubes());
        for (GeoBone child : source.getChildBones()) {
            copy.getChildBones().add(copyRestBone(child, copy));
        }
        return copy;
    }

    private record OwnedModel(BakedGeoModel source, BakedGeoModel model) {
    }
}
