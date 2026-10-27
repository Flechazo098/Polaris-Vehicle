package com.flechazo.polaris_vehicle.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.util.Arrays;

/**
 * A slope quad carrying geometry-derived ambient visibility for its four vertices.
 */
public final class RampAoQuad extends BakedQuad {
    private static final int VERTEX_COUNT = 4;
    private final float[] ambientVisibility;

    public RampAoQuad(
            int[] vertices,
            int tintIndex,
            Direction direction,
            TextureAtlasSprite sprite,
            boolean shade,
            float[] ambientVisibility
    ) {
        super(vertices, tintIndex, direction, sprite, shade, false);
        if (ambientVisibility.length != VERTEX_COUNT) {
            throw new IllegalArgumentException("A quad requires exactly four AO values");
        }
        this.ambientVisibility = Arrays.copyOf(ambientVisibility, VERTEX_COUNT);
        for (float value : this.ambientVisibility) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("AO visibility must be finite and inside [0,1]");
            }
        }
    }

    public static RampAoQuad copyOf(BakedQuad quad, float[] ambientVisibility) {
        return new RampAoQuad(
                quad.getVertices(),
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade(),
                ambientVisibility
        );
    }

    public float ambientVisibility(int vertex) {
        return ambientVisibility[vertex];
    }

    public float[] copyAmbientVisibility() {
        return Arrays.copyOf(ambientVisibility, VERTEX_COUNT);
    }
}
