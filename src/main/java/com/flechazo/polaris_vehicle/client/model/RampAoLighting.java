package com.flechazo.polaris_vehicle.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;

import java.util.Arrays;

public final class RampAoLighting {
    public static float[] appliedCopy(BakedQuad quad, float[] brightness) {
        if (!(quad instanceof RampAoQuad ramp) || !Minecraft.useAmbientOcclusion()) {
            return brightness;
        }
        float[] result = Arrays.copyOf(brightness, brightness.length);
        apply(ramp, result);
        return result;
    }

    public static void apply(RampAoQuad quad, float[] brightness) {
        if (!Minecraft.useAmbientOcclusion()) {
            return;
        }
        int count = Math.min(4, brightness.length);
        for (int vertex = 0; vertex < count; vertex++) {
            brightness[vertex] *= quad.ambientVisibility(vertex);
        }
    }
}
