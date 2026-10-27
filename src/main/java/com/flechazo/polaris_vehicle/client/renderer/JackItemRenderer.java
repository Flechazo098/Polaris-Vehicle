package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.JackModel;
import com.flechazo.polaris_vehicle.item.VehicleJackItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public final class JackItemRenderer extends StaticGeoItemRenderer<VehicleJackItem> {
    private final JackModel jackModel;
    private boolean placed;

    public JackItemRenderer() {
        this(new JackModel());
    }

    JackItemRenderer(JackModel model) {
        super(model);
        jackModel = model;
    }

    @Override
    protected boolean usesGuiSprite() {
        return false;
    }

    public void renderPlaced(ItemStack stack, double liftTick, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        placed = true;
        jackModel.setLiftTick(liftTick);
        try {
            super.renderByItem(stack, ItemDisplayContext.FIXED, pose, buffers, light, overlay);
        } finally {
            placed = false;
            jackModel.setLiftTick(Double.NaN);
        }
    }

    @Override
    protected void prepareModel(VehicleJackItem item, BakedGeoModel model) {
        jackModel.applyPose(item, model);
    }

    @Override
    public void preRender(PoseStack pose, VehicleJackItem item, BakedGeoModel model,
                          @Nullable MultiBufferSource buffers, @Nullable VertexConsumer buffer, boolean reRender,
                          float partialTick, int light, int overlay, int colour) {
        if (!placed) {
            super.preRender(pose, item, model, buffers, buffer, reRender, partialTick, light, overlay, colour);
            return;
        }
        itemRenderTranslations = new Matrix4f(pose.last().pose());
        if (!reRender) {
            prepareModel(item, model);
            pose.translate(.5F, .51F, .5F);
        }
    }
}
