package com.flechazo.polaris_vehicle.client.compat.render;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratedVertexConsumer;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.github.argon4w.acceleratedrendering.features.mods.ModsFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.cache.object.GeoBone;

public final class AcceleratedVehicleCompat {

    @SuppressWarnings("unchecked")
    public static boolean render(PoseStack stack, GeoBone bone, VertexConsumer buffer, int light, int overlay, int color) {
        if (!(buffer instanceof IAcceleratedVertexConsumer extension)
                || !(bone instanceof IAcceleratedRenderer<?> renderer)
                || !CoreFeature.isLoaded()
                || !AcceleratedEntityRenderingFeature.isEnabled()
                || !AcceleratedEntityRenderingFeature.shouldUseAcceleratedPipeline()
                || !ModsFeature.isEnabled() || !ModsFeature.shouldAccelerateGecko()
                || !(CoreFeature.isRenderingLevel() || (CoreFeature.isRenderingGui()
                && AcceleratedEntityRenderingFeature.shouldAccelerateInGui()))
                || !extension.isAccelerated()) return false;
        extension.doRender((IAcceleratedRenderer<Void>) renderer, null, stack.last().pose(), stack.last().normal(),
                light, overlay, color);
        return true;
    }
}
