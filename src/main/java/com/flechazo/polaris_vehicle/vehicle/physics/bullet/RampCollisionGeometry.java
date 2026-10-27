package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Vector3f;
import net.minecraft.core.Direction;

/**
 * Local-space convex geometry shared by the rendered half-block ramp.
 */
public final class RampCollisionGeometry {
    private static final float HALF_BLOCK = 0.5F;
    private static final float RAMP_RISE = 0.5F;

    private RampCollisionGeometry() {
    }

    /**
     * Six vertices of a triangular prism centered on the Minecraft block.
     * Local Y therefore ranges from -0.5 at the block floor to 0 at the high edge.
     */
    public static Vector3f[] vertices(Direction highEdge) {
        return vertices(highEdge, false);
    }

    /**
     * Convex lower (0..0.5) or upper (0.5..1.0) half-ramp solid.
     */
    public static Vector3f[] vertices(Direction highEdge, boolean upper) {
        return vertices(highEdge, upper, false);
    }

    /**
     * Convex lower, upper-continuation, or full-height ramp solid.
     */
    public static Vector3f[] vertices(Direction highEdge, boolean upper, boolean fullHeight) {
        requireHorizontal(highEdge);
        if (upper && fullHeight) {
            throw new IllegalArgumentException("A ramp cannot be both upper and full-height");
        }
        return vertices(highEdge, upper ? 0.5F : 0F, fullHeight ? 1F : 0.5F);
    }

    /**
     * General solid ramp, with low-edge height and rise measured from the block floor.
     */
    public static Vector3f[] vertices(Direction highEdge, float lowHeight, float rise) {
        requireHorizontal(highEdge);
        float lowY = lowHeight - HALF_BLOCK;
        float highY = lowY + rise;
        Vector3f[] southFacing = lowHeight > 0
                ? new Vector3f[]{
                new Vector3f(-HALF_BLOCK, -HALF_BLOCK, -HALF_BLOCK),
                new Vector3f(-HALF_BLOCK, -HALF_BLOCK, HALF_BLOCK),
                new Vector3f(HALF_BLOCK, -HALF_BLOCK, -HALF_BLOCK),
                new Vector3f(HALF_BLOCK, -HALF_BLOCK, HALF_BLOCK),
                new Vector3f(-HALF_BLOCK, lowY, -HALF_BLOCK),
                new Vector3f(HALF_BLOCK, lowY, -HALF_BLOCK),
                new Vector3f(-HALF_BLOCK, highY, HALF_BLOCK),
                new Vector3f(HALF_BLOCK, highY, HALF_BLOCK)
        } : new Vector3f[]{
                new Vector3f(-HALF_BLOCK, -HALF_BLOCK, -HALF_BLOCK),
                new Vector3f(-HALF_BLOCK, -HALF_BLOCK, HALF_BLOCK),
                new Vector3f(HALF_BLOCK, -HALF_BLOCK, -HALF_BLOCK),
                new Vector3f(HALF_BLOCK, -HALF_BLOCK, HALF_BLOCK),
                new Vector3f(-HALF_BLOCK, highY, HALF_BLOCK),
                new Vector3f(HALF_BLOCK, highY, HALF_BLOCK)
        };
        double angle = switch (highEdge) {
            case SOUTH -> 0.0;
            case EAST -> Math.PI * 0.5;
            case NORTH -> Math.PI;
            case WEST -> Math.PI * 1.5;
            default -> throw new AssertionError(highEdge);
        };
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        for (Vector3f vertex : southFacing) {
            float x = vertex.x;
            vertex.x = (float) (cosine * x + sine * vertex.z);
            vertex.z = (float) (-sine * x + cosine * vertex.z);
        }
        return southFacing;
    }

    /**
     * Local-space height of the sloped face at a point inside the block footprint.
     */
    public static float surfaceY(Direction highEdge, float localX, float localZ) {
        return surfaceY(highEdge, localX, localZ, false);
    }

    public static float surfaceY(
            Direction highEdge,
            float localX,
            float localZ,
            boolean upper
    ) {
        return surfaceY(highEdge, localX, localZ, upper, false);
    }

    public static float surfaceY(
            Direction highEdge,
            float localX,
            float localZ,
            boolean upper,
            boolean fullHeight
    ) {
        return surfaceY(highEdge, localX, localZ, upper ? 0.5F : 0F, fullHeight ? 1F : RAMP_RISE);
    }

    public static float surfaceY(Direction highEdge, float localX, float localZ, float lowHeight, float rise) {
        requireHorizontal(highEdge);
        float along = highEdge.getStepX() * localX + highEdge.getStepZ() * localZ;
        return lowHeight - HALF_BLOCK + rise * (along + HALF_BLOCK);
    }

    /**
     * Unit normal of the sloped face, pointing out of the solid.
     */
    public static Vector3f surfaceNormal(Direction highEdge) {
        return surfaceNormal(highEdge, false);
    }

    public static Vector3f surfaceNormal(Direction highEdge, boolean fullHeight) {
        return surfaceNormal(highEdge, fullHeight ? 1F : RAMP_RISE);
    }

    public static Vector3f surfaceNormal(Direction highEdge, float rise) {
        requireHorizontal(highEdge);
        return new Vector3f(
                -rise * highEdge.getStepX(),
                1.0F,
                -rise * highEdge.getStepZ()
        ).normalize();
    }

    private static void requireHorizontal(Direction direction) {
        if (direction.getAxis().isVertical()) {
            throw new IllegalArgumentException("Ramp edge must be horizontal: " + direction);
        }
    }
}
