package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.content.block.RampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Finite-radius cosine-weighted hemisphere visibility for a real ramp surface.
 *
 * <p>This is evaluated during chunk rebuilding. Rays traverse block cells and
 * intersect each candidate through {@link VoxelShape#clip}; the custom ramp shape therefore uses
 * its exact convex prism.
 */
final class SlopeAmbientOcclusion {
    private static final int RAY_COUNT = 24;
    private static final double RADIUS = 1.5D;
    private static final double ORIGIN_EPSILON = 1.0E-4D;
    private static final double DIRECTION_EPSILON = 1.0E-9D;
    private static final int MAX_DDA_STEPS = 16;
    private static final Occluder EMPTY = new Occluder(Shapes.empty(), 0.0F);

    private final BlockAndTintGetter level;
    private final BlockPos rampPosition;
    private final Vec3 normal;
    private final Vec3 tangent;
    private final Vec3 bitangent;
    private final Vec3[] directions;
    private final Map<Long, Float> samples = new HashMap<>();
    private final Map<Long, Occluder> occluders = new HashMap<>();

    SlopeAmbientOcclusion(
            BlockAndTintGetter level,
            BlockPos rampPosition,
            Direction highEdge,
            boolean fullHeight
    ) {
        this(level, rampPosition, highEdge, fullHeight ? 1D : 0.5D);
    }

    SlopeAmbientOcclusion(BlockAndTintGetter level, BlockPos rampPosition, Direction highEdge, double rise) {
        this.level = level;
        this.rampPosition = rampPosition;
        this.normal = new Vec3(
                -rise * highEdge.getStepX(),
                1.0D,
                -rise * highEdge.getStepZ()
        ).normalize();
        this.tangent = new Vec3(
                -highEdge.getStepZ(),
                0.0D,
                highEdge.getStepX()
        ).normalize();
        this.bitangent = normal.cross(tangent).normalize();
        this.directions = buildDirections();
    }

    float sample(float localX, float localY, float localZ) {
        long key = sampleKey(localX, localY, localZ);
        return samples.computeIfAbsent(key, ignored -> trace(localX, localY, localZ));
    }

    private float trace(float localX, float localY, float localZ) {
        Vec3 point = new Vec3(
                rampPosition.getX() + localX,
                rampPosition.getY() + localY,
                rampPosition.getZ() + localZ
        );
        Vec3 start = point.add(normal.scale(ORIGIN_EPSILON));
        double visibility = 0.0D;
        for (Vec3 direction : directions) {
            visibility += traceDirection(start, direction);
        }
        return Mth.clamp((float) (visibility / directions.length), 0.0F, 1.0F);
    }

    private double traceDirection(Vec3 start, Vec3 direction) {
        Vec3 end = start.add(direction.scale(RADIUS));
        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int stepX = step(direction.x);
        int stepY = step(direction.y);
        int stepZ = step(direction.z);
        double tMaxX = firstBoundaryDistance(start.x, direction.x, x, stepX);
        double tMaxY = firstBoundaryDistance(start.y, direction.y, y, stepY);
        double tMaxZ = firstBoundaryDistance(start.z, direction.z, z, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.x);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.y);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / direction.z);
        double travelled = 0.0D;
        double transmission = 1.0D;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int iteration = 0; iteration < MAX_DDA_STEPS && travelled <= RADIUS;
             iteration++) {
            cursor.set(x, y, z);
            Occluder occluder = occluderAt(cursor);
            if (occluder.opacity() > 0.0F) {
                BlockHitResult hit = occluder.shape().clip(start, end, cursor);
                if (hit != null) {
                    double distance = start.distanceTo(hit.getLocation());
                    if (distance <= RADIUS + ORIGIN_EPSILON) {
                        double proximity = 1.0D - Mth.clamp(distance / RADIUS, 0.0D, 1.0D);
                        double falloff = proximity * proximity * (3.0D - 2.0D * proximity);
                        transmission *= 1.0D - occluder.opacity() * falloff;
                        if (transmission <= 1.0E-3D || occluder.opacity() >= 0.999F) {
                            return Math.max(0.0D, transmission);
                        }
                    }
                }
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                travelled = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                travelled = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                travelled = tMaxZ;
                tMaxZ += tDeltaZ;
            }
        }
        return transmission;
    }

    private Occluder occluderAt(BlockPos position) {
        long key = position.asLong();
        Occluder cached = occluders.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState state = level.getBlockState(position);
        Occluder result;
        if (state.isAir()) {
            result = EMPTY;
        } else if (state.getBlock() instanceof RampBlock) {
            VoxelShape shape = state.getCollisionShape(level, position);
            result = shape.isEmpty() ? EMPTY : new Occluder(shape, 1.0F);
        } else {
            float opacity = Mth.clamp(state.getLightBlock(level, position) / 15.0F,
                    0.0F, 1.0F);
            VoxelShape shape = opacity <= 0.0F
                    ? Shapes.empty()
                    : state.getOcclusionShape(level, position);
            result = shape.isEmpty() ? EMPTY : new Occluder(shape, opacity);
        }
        occluders.put(key, result);
        return result;
    }

    private Vec3[] buildDirections() {
        Vec3[] result = new Vec3[RAY_COUNT];
        for (int index = 0; index < result.length; index++) {
            // Hammersley points mapped to a cosine-weighted hemisphere. Keeping the sequence
            // fixed in the slope's tangent frame makes adjacent lower/upper blocks seam-free.
            double u = (index + 0.5D) / result.length;
            double v = radicalInverse(index);
            double radius = Math.sqrt(u);
            double angle = Math.PI * 2.0D * v;
            double tangentWeight = radius * Math.cos(angle);
            double bitangentWeight = radius * Math.sin(angle);
            double normalWeight = Math.sqrt(1.0D - u);
            result[index] = tangent.scale(tangentWeight)
                    .add(bitangent.scale(bitangentWeight))
                    .add(normal.scale(normalWeight))
                    .normalize();
        }
        return result;
    }

    private static int step(double direction) {
        if (direction > DIRECTION_EPSILON) {
            return 1;
        }
        if (direction < -DIRECTION_EPSILON) {
            return -1;
        }
        return 0;
    }

    private static double firstBoundaryDistance(
            double coordinate,
            double direction,
            int cell,
            int step
    ) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0D : cell;
        return Math.max(0.0D, (boundary - coordinate) / direction);
    }

    private static double radicalInverse(int value) {
        return Integer.toUnsignedLong(Integer.reverse(value)) * 0x1.0p-32;
    }

    private static long sampleKey(float x, float y, float z) {
        long qx = Math.round(x * 4096.0F) & 0x1FFFFFL;
        long qy = Math.round(y * 4096.0F) & 0x1FFFFFL;
        long qz = Math.round(z * 4096.0F) & 0x1FFFFFL;
        return qx | qy << 21 | qz << 42;
    }

    private record Occluder(VoxelShape shape, float opacity) {
    }
}
