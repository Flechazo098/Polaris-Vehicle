package com.flechazo.polaris_vehicle.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class GuiSpriteItemRenderer {
    static boolean render(
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ModelResourceLocation modelId = ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(
                        itemId.getNamespace(),
                        "item/" + itemId.getPath() + "_2d"
                )
        );
        BakedModel model = minecraft.getModelManager().getModel(modelId);
        if (model == minecraft.getModelManager().getMissingModel()) {
            return false;
        }

        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        Lighting.setupForFlatItems();
        try {
            for (BakedModel pass : model.getRenderPasses(stack, true)) {
                for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                    VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(
                            bufferSource,
                            renderType,
                            true,
                            stack.hasFoil()
                    );
                    itemRenderer.renderModelLists(
                            pass,
                            stack,
                            packedLight,
                            packedOverlay,
                            poseStack,
                            vertices
                    );
                }
            }
            if (bufferSource instanceof MultiBufferSource.BufferSource immediate) {
                immediate.endBatch();
            }
            RenderSystem.enableDepthTest();
        } finally {
            Lighting.setupFor3DItems();
        }
        return true;
    }
}
