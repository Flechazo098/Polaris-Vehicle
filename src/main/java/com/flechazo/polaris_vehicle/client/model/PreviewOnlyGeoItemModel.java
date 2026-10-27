package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.client.renderer.ItemPreviewRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public final class PreviewOnlyGeoItemModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel sprite;

    public PreviewOnlyGeoItemModel(BakedModel geometry, BakedModel sprite) {
        super(geometry);
        this.sprite = sprite;
    }

    private BakedModel displayModel() {
        return ItemPreviewRenderContext.isActive() ? originalModel : sprite;
    }

    @Override
    public boolean isGui3d() {
        return displayModel().isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return displayModel().usesBlockLight();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return displayModel().useAmbientOcclusion();
    }

    @Override
    public ItemTransforms getTransforms() {
        return displayModel().getTransforms();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        return displayModel().applyTransform(context, pose, leftHand);
    }
}
