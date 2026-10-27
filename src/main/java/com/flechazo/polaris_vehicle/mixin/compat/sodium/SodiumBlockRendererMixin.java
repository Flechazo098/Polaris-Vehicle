package com.flechazo.polaris_vehicle.mixin.compat.sodium;

import com.flechazo.polaris_vehicle.client.compat.render.ClientRenderCompatRegistry;
import com.flechazo.polaris_vehicle.client.compat.render.SodiumRampAoCarrier;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Arrays;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin {
    @ModifyArgs(
            method = "processQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/"
                            + "BlockRenderer;bufferQuad("
                            + "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;"
                            + "[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
            )
    )
    private void polarisVehicle$applySlopeAo(Args args) {
        if (!ClientRenderCompatRegistry.isActive(ClientRenderCompatRegistry.SODIUM)
                || !Minecraft.useAmbientOcclusion()) {
            return;
        }
        MutableQuadViewImpl quad = args.get(0);
        float[] visibility = ((SodiumRampAoCarrier) quad)
                .polarisVehicle$ambientVisibility();
        if (visibility == null) {
            return;
        }
        float[] brightness = args.get(1);
        float[] result = Arrays.copyOf(brightness, brightness.length);
        int count = Math.min(Math.min(4, result.length), visibility.length);
        for (int vertex = 0; vertex < count; vertex++) {
            result[vertex] *= visibility[vertex];
        }
        args.set(1, result);
    }
}
