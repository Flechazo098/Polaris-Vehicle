package com.flechazo.polaris_vehicle.content.block;

import com.flechazo.polaris_vehicle.vehicle.collision.OrientedBoundingBox;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.RampCollisionGeometry;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RampShape extends VoxelShape {
    private static final double EPSILON = 1.0E-7;
    private final Direction highEdge;
    private final boolean upper;
    private final boolean fullHeight;
    private final float lowHeight;
    private final float rise;
    private final Vec3 offset;
    private final List<Vec3> corners;
    private final AABB bounds;

    public RampShape(Direction highEdge, boolean upper) {
        this(highEdge, upper, false, Vec3.ZERO);
    }

    public RampShape(Direction highEdge, boolean upper, boolean fullHeight) {
        this(highEdge, upper, fullHeight, Vec3.ZERO);
    }

    RampShape(Direction highEdge, boolean upper, boolean fullHeight, Vec3 offset) {
        this(highEdge, upper ? 0.5F : 0F, fullHeight ? 1F : 0.5F, offset);
    }

    public RampShape(Direction highEdge, float lowHeight, float rise) {
        this(highEdge, lowHeight, rise, Vec3.ZERO);
    }

    RampShape(Direction highEdge, float lowHeight, float rise, Vec3 offset) {
        super(BitSetDiscreteVoxelShape.withFilledBounds(1, 3, 1, 0, 0, 0, 1, 2, 1));
        this.highEdge = highEdge;
        this.upper = lowHeight > 0;
        this.lowHeight = lowHeight;
        this.rise = rise;
        this.fullHeight = rise == 1F;
        this.offset = offset;
        this.corners = Arrays.stream(RampCollisionGeometry.vertices(
                        highEdge, lowHeight, rise))
                .map(vertex -> new Vec3(vertex.x + 0.5, vertex.y + 0.5, vertex.z + 0.5))
                .toList();
        this.bounds = new AABB(offset.x, offset.y, offset.z,
                offset.x + 1.0,
                offset.y + (lowHeight + rise + (lowHeight + rise >= 1 ? -0.0000625 : 0.0000625)),
                offset.z + 1.0);
    }

    public double supportHeight(AABB footprint) {
        if (footprint.maxX <= offset.x || footprint.minX >= offset.x + 1.0
                || footprint.maxZ <= offset.z || footprint.minZ >= offset.z + 1.0) {
            return Double.NEGATIVE_INFINITY;
        }
        double x = highEdge.getStepX() > 0 ? Math.min(footprint.maxX - offset.x, 1.0)
                : Math.max(footprint.minX - offset.x, 0.0);
        double z = highEdge.getStepZ() > 0 ? Math.min(footprint.maxZ - offset.z, 1.0)
                : Math.max(footprint.minZ - offset.z, 0.0);
        return offset.y + 0.5 + RampCollisionGeometry.surfaceY(
                highEdge, (float) (x - 0.5), (float) (z - 0.5), lowHeight, rise);
    }

    public @Nullable SweepHit sweep(AABB box, Vec3 movement) {
        if (!bounds.inflate(EPSILON).intersects(box.expandTowards(movement))) {
            return null;
        }
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        Vec3 normal = Vec3.ZERO;
        Vec3 slope = new Vec3(-rise * highEdge.getStepX(), 1.0,
                -rise * highEdge.getStepZ()).normalize();
        for (Vec3 axis : List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                new Vec3(0, 0, 1), slope)) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Vec3 corner : corners) {
                double projected = corner.add(offset).dot(axis);
                min = Math.min(min, projected);
                max = Math.max(max, projected);
            }
            double boxMin = projectionMin(box, axis);
            double boxMax = -projectionMin(box, axis.scale(-1));
            double speed = movement.dot(axis);
            if (Math.abs(speed) < EPSILON) {
                if (boxMax <= min + EPSILON || boxMin >= max - EPSILON) {
                    return null;
                }
                continue;
            }
            double near = (min - boxMax) / speed;
            double far = (max - boxMin) / speed;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            if (near > entry) {
                entry = near;
                normal = axis.scale(speed > 0 ? -1.0 : 1.0);
            }
            exit = Math.min(exit, far);
            if (entry > exit + EPSILON) {
                return null;
            }
        }
        double inwardSpeed = -movement.dot(normal);
        return -entry * inwardSpeed > EPSILON || entry > 1 || exit < 0
                || inwardSpeed <= EPSILON ? null : new SweepHit(Math.max(0, entry), normal);
    }

    public boolean intersects(AABB box) {
        if (!bounds.intersects(box)) {
            return false;
        }
        Vec3 slope = new Vec3(-rise * highEdge.getStepX(), 1.0,
                -rise * highEdge.getStepZ()).normalize();
        for (Vec3 axis : List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                new Vec3(0, 0, 1), slope)) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (Vec3 corner : corners) {
                double projected = corner.add(offset).dot(axis);
                minimum = Math.min(minimum, projected);
                maximum = Math.max(maximum, projected);
            }
            if (-projectionMin(box, axis.scale(-1)) <= minimum + EPSILON
                    || projectionMin(box, axis) >= maximum - EPSILON) {
                return false;
            }
        }
        return true;
    }

    public @Nullable SweepHit sweep(OrientedBoundingBox box, Vec3 movement) {
        if (!bounds.inflate(EPSILON).intersects(box.enclosingAabb().expandTowards(movement))) {
            return null;
        }
        Vec3 across = new Vec3(-highEdge.getStepZ(), 0, highEdge.getStepX());
        Vec3 alongSlope = new Vec3(highEdge.getStepX(), rise, highEdge.getStepZ());
        List<Vec3> axes = new ArrayList<>(16);
        axes.addAll(List.of(new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                new Vec3(0, 0, 1),
                new Vec3(-rise * highEdge.getStepX(), 1,
                        -rise * highEdge.getStepZ())));
        List<Vec3> basis = List.of(box.axisX(), box.axisY(), box.axisZ());
        axes.addAll(basis);
        for (Vec3 edge : List.of(across, new Vec3(0, 1, 0), alongSlope)) {
            for (Vec3 direction : basis) {
                axes.add(edge.cross(direction));
            }
        }

        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        Vec3 normal = Vec3.ZERO;
        for (Vec3 candidate : axes) {
            if (candidate.lengthSqr() <= EPSILON * EPSILON) {
                continue;
            }
            Vec3 axis = candidate.normalize();
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (Vec3 corner : corners) {
                double projected = corner.add(offset).dot(axis);
                minimum = Math.min(minimum, projected);
                maximum = Math.max(maximum, projected);
            }
            double center = box.center().dot(axis);
            double radius = box.projectedRadius(axis);
            double speed = movement.dot(axis);
            if (Math.abs(speed) < EPSILON) {
                if (center + radius <= minimum + EPSILON
                        || center - radius >= maximum - EPSILON) {
                    return null;
                }
                continue;
            }
            double near = (minimum - center - radius) / speed;
            double far = (maximum - center + radius) / speed;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            if (near > entry) {
                entry = near;
                normal = axis.scale(speed > 0 ? -1 : 1);
            }
            exit = Math.min(exit, far);
            if (entry > exit + EPSILON) {
                return null;
            }
        }
        double inwardSpeed = -movement.dot(normal);
        return -entry * inwardSpeed > EPSILON || entry > 1 || exit < 0
                || inwardSpeed <= EPSILON ? null : new SweepHit(Math.max(0, entry), normal);
    }

    private static double projectionMin(AABB box, Vec3 axis) {
        return component(axis.x, box.minX, box.maxX)
                + component(axis.y, box.minY, box.maxY)
                + component(axis.z, box.minZ, box.maxZ);
    }

    private static double component(double axis, double min, double max) {
        return axis * (axis >= 0 ? min : max);
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> DoubleList.of(bounds.minX, bounds.maxX);
            case Y -> DoubleList.of(bounds.minY, (bounds.minY + bounds.maxY) * 0.5,
                    bounds.maxY, bounds.maxY + 0.001);
            case Z -> DoubleList.of(bounds.minZ, bounds.maxZ);
        };
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        return new RampShape(highEdge, lowHeight, rise, offset.add(x, y, z));
    }

    @Override
    public VoxelShape optimize() {
        return this;
    }

    @Override
    public AABB bounds() {
        return bounds;
    }

    @Override
    public double collide(Direction.Axis axis, AABB box, double distance) {
        if (Math.abs(distance) < EPSILON) {
            return distance;
        }
        SweepHit hit = sweep(box, Vec3.ZERO.with(axis, distance));
        return hit == null ? distance : distance * hit.time();
    }

    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        consumer.consume(bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ);
    }

    @Override
    public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
        int[][] edges = upper
                ? new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {0, 4}, {2, 5}, {1, 6}, {3, 7},
                {4, 5}, {4, 6}, {5, 7}, {6, 7}}
                : new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {1, 4}, {3, 5},
                {0, 4}, {2, 5}, {4, 5}};
        for (int[] edge : edges) {
            Vec3 start = corners.get(edge[0]).add(offset);
            Vec3 end = corners.get(edge[1]).add(offset);
            consumer.consume(start.x, start.y, start.z, end.x, end.y, end.z);
        }
    }

    @Override
    public @Nullable BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos) {
        Vec3 start = from.subtract(Vec3.atLowerCornerOf(pos)).subtract(offset);
        Vec3 direction = to.subtract(from);
        double entry = 0.0;
        double exit = 1.0;
        Vec3 normal = Vec3.ZERO;
        Vec3[] normals = {
                new Vec3(-1, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, -1), new Vec3(0, 0, 1),
                new Vec3(0, -1, 0),
                new Vec3(-rise * highEdge.getStepX(), 1,
                        -rise * highEdge.getStepZ())
        };
        for (Vec3 axis : normals) {
            double plane = Double.NEGATIVE_INFINITY;
            for (Vec3 corner : corners) {
                plane = Math.max(plane, corner.dot(axis));
            }
            double d = plane - start.dot(axis);
            double velocity = direction.dot(axis);
            if (velocity < -EPSILON) {
                double time = d / velocity;
                if (time > entry) {
                    entry = time;
                    normal = axis;
                }
            } else if (Math.abs(velocity) < EPSILON && d < -EPSILON) {
                return null;
            } else if (velocity > EPSILON) {
                exit = Math.min(exit, d / velocity);
            }
            if (entry > exit + EPSILON) {
                return null;
            }
        }
        if (entry > 1 || exit < 0) {
            return null;
        }
        boolean inside = normal.equals(Vec3.ZERO);
        Vec3 hit = from.add(direction.scale(inside ? 0.001 : entry));
        Vec3 face = inside ? direction.scale(-1) : normal;
        return new BlockHitResult(hit,
                Direction.getNearest((float) face.x, (float) face.y, (float) face.z),
                pos, inside);
    }

    public record SweepHit(double time, Vec3 normal) {
    }
}
