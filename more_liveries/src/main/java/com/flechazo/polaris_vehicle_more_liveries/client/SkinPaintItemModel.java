package com.flechazo.polaris_vehicle_more_liveries.client;

import com.flechazo.polaris_vehicle.client.renderer.ItemPreviewRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

final class SkinPaintItemModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel sprite;

    SkinPaintItemModel(BakedModel geometry, BakedModel sprite) {
        super(geometry);
        this.sprite = sprite;
    }

    @Override
    public boolean isGui3d() {
        return sprite.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return sprite.usesBlockLight();
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemPreviewRenderContext.isActive() ? sprite.getTransforms() : originalModel.getTransforms();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        BakedModel model = context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND
                || ItemPreviewRenderContext.isActive() ? sprite : originalModel;
        return model.applyTransform(context, pose, leftHand);
    }
}
