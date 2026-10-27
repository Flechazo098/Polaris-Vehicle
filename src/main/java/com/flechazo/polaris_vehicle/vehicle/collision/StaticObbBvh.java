package com.flechazo.polaris_vehicle.vehicle.collision;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable, flat binary BVH over local-space OBB leaves. Construction uses a
 * 12-bin surface-area heuristic; traversal reuses a per-thread integer stack
 * and performs no per-node allocation.
 */
public final class StaticObbBvh {
    private static final int MAX_LEAF_SIZE = 4;
    private static final int BIN_COUNT = 12;
    private static final double SPLIT_EPSILON = 1.0E-9;
    private static final AABB EMPTY_BUILD_BOUNDS = new AABB(
            0.0, 0.0, 0.0,
            0.0, 0.0, 0.0
    );

    private final List<VehicleCollisionCube> cubes;
    private final int[] leafOrder;
    private final double[] minX;
    private final double[] minY;
    private final double[] minZ;
    private final double[] maxX;
    private final double[] maxY;
    private final double[] maxZ;
    private final int[] left;
    private final int[] right;
    private final int[] firstLeaf;
    private final int[] leafCount;
    private final ThreadLocal<int[]> traversalStacks = ThreadLocal.withInitial(() -> new int[0]);

    StaticObbBvh(
            List<VehicleCollisionCube> cubes,
            int[] leafOrder,
            List<BuildNode> nodes
    ) {
        this.cubes = List.copyOf(cubes);
        this.leafOrder = leafOrder;
        int nodeCount = nodes.size();
        minX = new double[nodeCount];
        minY = new double[nodeCount];
        minZ = new double[nodeCount];
        maxX = new double[nodeCount];
        maxY = new double[nodeCount];
        maxZ = new double[nodeCount];
        left = new int[nodeCount];
        right = new int[nodeCount];
        firstLeaf = new int[nodeCount];
        leafCount = new int[nodeCount];
        for (int index = 0; index < nodeCount; index++) {
            BuildNode node = nodes.get(index);
            minX[index] = node.bounds.minX;
            minY[index] = node.bounds.minY;
            minZ[index] = node.bounds.minZ;
            maxX[index] = node.bounds.maxX;
            maxY[index] = node.bounds.maxY;
            maxZ[index] = node.bounds.maxZ;
            left[index] = node.left;
            right[index] = node.right;
            firstLeaf[index] = node.firstLeaf;
            leafCount[index] = node.leafCount;
        }
    }

    public static StaticObbBvh build(List<VehicleCollisionCube> cubes) {
        if (cubes.isEmpty()) {
            throw new IllegalArgumentException("Cannot build a BVH without cubes");
        }
        List<VehicleCollisionCube> stableCubes = List.copyOf(cubes);
        int[] order = new int[stableCubes.size()];
        Arrays.setAll(order, index -> index);
        List<BuildNode> nodes = new ArrayList<>(stableCubes.size() * 2);
        buildNode(stableCubes, order, 0, order.length, nodes);
        return new StaticObbBvh(stableCubes, order, nodes);
    }

    public AABB rootBounds() {
        return nodeBounds(0);
    }

    public int nodeCount() {
        return minX.length;
    }

    public boolean intersects(OrientedBoundingBox query, boolean solidOnly) {
        return intersects(query, VehicleCollisionRole.ALL_MASK, solidOnly);
    }

    public boolean intersects(
            OrientedBoundingBox query,
            int roleMask,
            boolean solidOnly
    ) {
        int[] stack = traversalStack();
        int size = 0;
        stack[size++] = 0;
        while (size > 0) {
            int nodeIndex = stack[--size];
            if (!intersectsNode(query, nodeIndex)) {
                continue;
            }
            int count = leafCount[nodeIndex];
            if (count > 0) {
                int end = firstLeaf[nodeIndex] + count;
                for (int leafIndex = firstLeaf[nodeIndex]; leafIndex < end; leafIndex++) {
                    VehicleCollisionCube cube = cubes.get(leafOrder[leafIndex]);
                    if (cube.participatesIn(roleMask, solidOnly)
                            && query.intersects(cube.box())) {
                        return true;
                    }
                }
                continue;
            }
            stack[size++] = left[nodeIndex];
            stack[size++] = right[nodeIndex];
        }
        return false;
    }

    /**
     * Number of broad-phase leaf candidates, primarily for profiling/tests.
     */
    public int candidateCount(OrientedBoundingBox query) {
        int candidates = 0;
        int[] stack = traversalStack();
        int size = 0;
        stack[size++] = 0;
        while (size > 0) {
            int nodeIndex = stack[--size];
            if (!intersectsNode(query, nodeIndex)) {
                continue;
            }
            if (leafCount[nodeIndex] > 0) {
                candidates += leafCount[nodeIndex];
            } else {
                stack[size++] = left[nodeIndex];
                stack[size++] = right[nodeIndex];
            }
        }
        return candidates;
    }

    public double sweepFraction(
            OrientedBoundingBox obstacle,
            Vec3 movement,
            boolean solidOnly
    ) {
        return sweepFraction(obstacle, movement, VehicleCollisionRole.ALL_MASK, solidOnly);
    }

    public double sweepFraction(
            OrientedBoundingBox obstacle,
            Vec3 movement,
            int roleMask,
            boolean solidOnly
    ) {
        double earliestFraction = Double.POSITIVE_INFINITY;
        int[] stack = traversalStack();
        int size = 0;
        stack[size++] = 0;
        while (size > 0) {
            int nodeIndex = stack[--size];
            if (!intersectsSweptNode(obstacle, movement, nodeIndex)) {
                continue;
            }
            int count = leafCount[nodeIndex];
            if (count > 0) {
                int end = firstLeaf[nodeIndex] + count;
                for (int leafIndex = firstLeaf[nodeIndex]; leafIndex < end; leafIndex++) {
                    VehicleCollisionCube cube = cubes.get(leafOrder[leafIndex]);
                    if (!cube.participatesIn(roleMask, solidOnly)) {
                        continue;
                    }
                    earliestFraction = Math.min(
                            earliestFraction,
                            cube.box().sweepFraction(obstacle, movement)
                    );
                    if (earliestFraction == 0.0) {
                        return 0.0;
                    }
                }
                continue;
            }
            stack[size++] = left[nodeIndex];
            stack[size++] = right[nodeIndex];
        }
        return earliestFraction;
    }

    private int[] traversalStack() {
        int[] stack = traversalStacks.get();
        if (stack.length < minX.length) {
            stack = new int[minX.length];
            traversalStacks.set(stack);
        }
        return stack;
    }

    private AABB nodeBounds(int nodeIndex) {
        return new AABB(
                minX[nodeIndex], minY[nodeIndex], minZ[nodeIndex],
                maxX[nodeIndex], maxY[nodeIndex], maxZ[nodeIndex]
        );
    }

    private boolean intersectsNode(OrientedBoundingBox query, int nodeIndex) {
        return query.intersects(
                minX[nodeIndex], minY[nodeIndex], minZ[nodeIndex],
                maxX[nodeIndex], maxY[nodeIndex], maxZ[nodeIndex]
        );
    }

    private boolean intersectsSweptNode(
            OrientedBoundingBox obstacle,
            Vec3 movement,
            int nodeIndex
    ) {
        return obstacle.intersects(
                minX[nodeIndex] + Math.min(0.0, movement.x),
                minY[nodeIndex] + Math.min(0.0, movement.y),
                minZ[nodeIndex] + Math.min(0.0, movement.z),
                maxX[nodeIndex] + Math.max(0.0, movement.x),
                maxY[nodeIndex] + Math.max(0.0, movement.y),
                maxZ[nodeIndex] + Math.max(0.0, movement.z)
        );
    }

    private static int buildNode(
            List<VehicleCollisionCube> cubes,
            int[] order,
            int start,
            int end,
            List<BuildNode> nodes
    ) {
        AABB bounds = boundsOf(cubes, order, start, end);
        int nodeIndex = nodes.size();
        nodes.add(BuildNode.placeholder(bounds));
        int count = end - start;
        if (count <= MAX_LEAF_SIZE) {
            nodes.set(nodeIndex, BuildNode.leaf(bounds, start, count));
            return nodeIndex;
        }

        Split split = chooseSplit(cubes, order, start, end);
        int middle = split.valid()
                ? partition(cubes, order, start, end, split.axis, split.position)
                : start + count / 2;
        if (middle == start || middle == end) {
            middle = start + count / 2;
        }
        int leftChild = buildNode(cubes, order, start, middle, nodes);
        int rightChild = buildNode(cubes, order, middle, end, nodes);
        nodes.set(nodeIndex, BuildNode.branch(bounds, leftChild, rightChild));
        return nodeIndex;
    }

    private static Split chooseSplit(
            List<VehicleCollisionCube> cubes,
            int[] order,
            int start,
            int end
    ) {
        Split best = Split.invalid();
        for (int axis = 0; axis < 3; axis++) {
            double centroidMin = Double.POSITIVE_INFINITY;
            double centroidMax = Double.NEGATIVE_INFINITY;
            for (int index = start; index < end; index++) {
                double centroid = cubes.get(order[index]).box().centerCoordinate(axis);
                centroidMin = Math.min(centroidMin, centroid);
                centroidMax = Math.max(centroidMax, centroid);
            }
            double extent = centroidMax - centroidMin;
            if (extent <= SPLIT_EPSILON) {
                continue;
            }

            Bin[] bins = new Bin[BIN_COUNT];
            Arrays.setAll(bins, ignored -> new Bin());
            for (int index = start; index < end; index++) {
                VehicleCollisionCube cube = cubes.get(order[index]);
                int binIndex = Math.min(
                        BIN_COUNT - 1,
                        (int) ((cube.box().centerCoordinate(axis) - centroidMin)
                                / extent * BIN_COUNT)
                );
                bins[binIndex].include(cube.localBounds());
            }

            AABB[] leftBounds = new AABB[BIN_COUNT - 1];
            AABB[] rightBounds = new AABB[BIN_COUNT - 1];
            int[] leftCounts = new int[BIN_COUNT - 1];
            int[] rightCounts = new int[BIN_COUNT - 1];
            AABB accumulated = EMPTY_BUILD_BOUNDS;
            boolean hasAccumulatedBounds = false;
            int accumulatedCount = 0;
            for (int bin = 0; bin < BIN_COUNT - 1; bin++) {
                if (bins[bin].count > 0) {
                    accumulated = hasAccumulatedBounds
                            ? union(accumulated, bins[bin].bounds)
                            : bins[bin].bounds;
                    hasAccumulatedBounds = true;
                    accumulatedCount += bins[bin].count;
                }
                leftBounds[bin] = accumulated;
                leftCounts[bin] = accumulatedCount;
            }
            accumulated = EMPTY_BUILD_BOUNDS;
            hasAccumulatedBounds = false;
            accumulatedCount = 0;
            for (int bin = BIN_COUNT - 1; bin > 0; bin--) {
                if (bins[bin].count > 0) {
                    accumulated = hasAccumulatedBounds
                            ? union(accumulated, bins[bin].bounds)
                            : bins[bin].bounds;
                    hasAccumulatedBounds = true;
                    accumulatedCount += bins[bin].count;
                }
                rightBounds[bin - 1] = accumulated;
                rightCounts[bin - 1] = accumulatedCount;
            }
            for (int bin = 0; bin < BIN_COUNT - 1; bin++) {
                if (leftCounts[bin] == 0 || rightCounts[bin] == 0) {
                    continue;
                }
                double cost = surfaceArea(leftBounds[bin]) * leftCounts[bin]
                        + surfaceArea(rightBounds[bin]) * rightCounts[bin];
                if (cost < best.cost) {
                    double position = centroidMin + extent * (bin + 1.0) / BIN_COUNT;
                    best = new Split(axis, position, cost);
                }
            }
        }
        return best;
    }

    private static int partition(
            List<VehicleCollisionCube> cubes,
            int[] order,
            int start,
            int end,
            int axis,
            double position
    ) {
        int low = start;
        int high = end - 1;
        while (low <= high) {
            if (cubes.get(order[low]).box().centerCoordinate(axis) < position) {
                low++;
                continue;
            }
            int swap = order[low];
            order[low] = order[high];
            order[high] = swap;
            high--;
        }
        return low;
    }

    private static AABB boundsOf(
            List<VehicleCollisionCube> cubes,
            int[] order,
            int start,
            int end
    ) {
        AABB bounds = cubes.get(order[start]).localBounds();
        for (int index = start + 1; index < end; index++) {
            bounds = union(bounds, cubes.get(order[index]).localBounds());
        }
        return bounds;
    }

    private static AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ)
        );
    }

    private static double surfaceArea(AABB bounds) {
        double x = bounds.maxX - bounds.minX;
        double y = bounds.maxY - bounds.minY;
        double z = bounds.maxZ - bounds.minZ;
        return 2.0 * (x * y + x * z + y * z);
    }

    private record BuildNode(
            AABB bounds,
            int left,
            int right,
            int firstLeaf,
            int leafCount
    ) {
        private static BuildNode leaf(AABB bounds, int firstLeaf, int leafCount) {
            return new BuildNode(bounds, -1, -1, firstLeaf, leafCount);
        }

        private static BuildNode branch(AABB bounds, int left, int right) {
            return new BuildNode(bounds, left, right, -1, 0);
        }

        private static BuildNode placeholder(AABB bounds) {
            return new BuildNode(bounds, -1, -1, -1, 0);
        }
    }

    private record Split(int axis, double position, double cost) {
        private static Split invalid() {
            return new Split(-1, 0.0, Double.POSITIVE_INFINITY);
        }

        private boolean valid() {
            return axis >= 0;
        }
    }

    private static final class Bin {
        private AABB bounds = EMPTY_BUILD_BOUNDS;
        private int count;

        private void include(AABB included) {
            bounds = count == 0 ? included : union(bounds, included);
            count++;
        }
    }
}
