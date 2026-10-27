package com.flechazo.polaris_vehicle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public final class GroundSpriteItemModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel groundModel;
    private final ItemTransforms transforms;

    public GroundSpriteItemModel(BakedModel originalModel, BakedModel groundModel) {
        super(originalModel);
        this.groundModel = groundModel;
        ItemTransforms original = originalModel.getTransforms();
        transforms = new ItemTransforms(
                original.thirdPersonLeftHand, original.thirdPersonRightHand,
                original.firstPersonLeftHand, original.firstPersonRightHand,
                original.head, original.gui, groundModel.getTransforms().ground,
                original.fixed, original.moddedTransforms
        );
    }

    @Override
    public boolean isGui3d() {
        return groundModel.isGui3d();
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        if (context == ItemDisplayContext.GROUND) {
            return groundModel.applyTransform(context, poseStack, leftHand);
        }
        return originalModel.applyTransform(context, poseStack, leftHand);
    }
}
