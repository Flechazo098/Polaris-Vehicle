package com.flechazo.polaris_vehicle.mixin.compat.sodium;

import com.flechazo.polaris_vehicle.client.compat.render.SodiumRampAoCarrier;
import com.flechazo.polaris_vehicle.client.model.RampAoQuad;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(value = MutableQuadViewImpl.class, remap = false)
public abstract class MutableQuadViewImplMixin implements SodiumRampAoCarrier {
    @Unique
    private float @Nullable [] polarisVehicle$ambientVisibility;

    @Inject(
            method = "fromVanilla(Lnet/minecraft/client/renderer/block/model/BakedQuad;"
                    + "Lnet/fabricmc/fabric/api/renderer/v1/material/RenderMaterial;"
                    + "Lnet/minecraft/core/Direction;)"
                    + "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;",
            at = @At("HEAD")
    )
    private void polarisVehicle$captureSlopeAo(
            BakedQuad quad,
            RenderMaterial material,
            Direction cullFace,
            CallbackInfoReturnable<MutableQuadViewImpl> callback
    ) {
        polarisVehicle$setAmbientVisibility(
                quad instanceof RampAoQuad ramp ? ramp.copyAmbientVisibility() : null);
    }

    @Override
    public void polarisVehicle$setAmbientVisibility(float @Nullable [] visibility) {
        polarisVehicle$ambientVisibility = visibility == null
                ? null
                : Arrays.copyOf(visibility, visibility.length);
    }

    @Override
    public float @Nullable [] polarisVehicle$ambientVisibility() {
        return polarisVehicle$ambientVisibility;
    }
}
