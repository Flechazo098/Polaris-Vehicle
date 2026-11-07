// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.math.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class BlockMerger {
    private final BlockShapeManager shapes;

    public BlockMerger(PhysicsLevel level) {
        shapes = level.getBlockShapeManager();
    }

    public record MergedRect(
            int minX, int minZ, int maxX, int maxZ, int shapeType, float centerOffsetY) {
        public int getWidth() {
            return maxX - minX + 1;
        }

        public int getDepth() {
            return maxZ - minZ + 1;
        }

        public BoxCollisionShape toCollisionShape() {
            return new BoxCollisionShape(
                    new Vector3f(
                            getWidth() / 2F,
                            shapeType == 2 || shapeType == 3 ? 0.25F : 0.5F,
                            getDepth() / 2F));
        }

        public Vector3f getCenter(int y) {
            return new Vector3f(
                    minX + getWidth() / 2F - 8,
                    y + 0.5F + centerOffsetY - 8,
                    minZ + getDepth() / 2F - 8);
        }
    }

    private record CandidateRect(int x, int z, int width, int height, int shapeType, int area) {}

    public List<MergedRect> mergeLayer(int levelY, BlockState[][] states) {
        var merged = new ArrayList<MergedRect>();
        var processed = new boolean[16][16];
        for (var rect : mergeXDirectionFirst(states))
            if (rect.getWidth() > 1 || rect.getDepth() > 1) {
                merged.add(rect);
                markProcessed(
                        processed, rect.minX(), rect.minZ(), rect.getWidth(), rect.getDepth());
            }
        mergeZDirectionRemains(states, processed, merged);
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                var state = states[x][z];
                if (processed[x][z] || state == null || !shapes.isMergeableShape(state)) continue;
                merged.add(
                        new MergedRect(
                                x, z, x, z, shapes.getShapeType(state), centerOffset(state)));
                processed[x][z] = true;
            }
        return merged;
    }

    private List<MergedRect> mergeXDirectionFirst(BlockState[][] states) {
        var merged = new ArrayList<MergedRect>();
        var processed = new boolean[16][16];
        var right = new int[16][16];
        for (int z = 0; z < 16; z++) {
            int length = 0;
            for (int x = 15; x >= 0; x--) {
                var state = states[x][z];
                length = state != null && shapes.isMergeableShape(state) ? length + 1 : 0;
                right[x][z] = length;
            }
        }
        while (true) {
            var rect = findLargestRect(processed, right, states);
            if (rect == null) break;
            markProcessed(processed, rect.x(), rect.z(), rect.width(), rect.height());
            merged.add(
                    new MergedRect(
                            rect.x(),
                            rect.z(),
                            rect.x() + rect.width() - 1,
                            rect.z() + rect.height() - 1,
                            rect.shapeType(),
                            centerOffset(states[rect.x()][rect.z()])));
        }
        return merged;
    }

    private CandidateRect findLargestRect(
            boolean[][] processed, int[][] right, BlockState[][] states) {
        CandidateRect best = null;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                var state = states[x][z];
                if (processed[x][z] || state == null || !shapes.isMergeableShape(state)) continue;
                int type = shapes.getShapeType(state), minWidth = right[x][z];
                for (int endZ = z; endZ < 16; endZ++) {
                    if (endZ > z) {
                        var next = states[x][endZ];
                        if (processed[x][endZ]
                                || next == null
                                || !shapes.isMergeableShape(next)
                                || shapes.getShapeType(next) != type) break;
                        minWidth = Math.min(minWidth, right[x][endZ]);
                    }
                    int width = actualWidth(x, z, endZ, minWidth, processed, states, type),
                            area = width * (endZ - z + 1);
                    if (best == null || area > best.area())
                        best = new CandidateRect(x, z, width, endZ - z + 1, type, area);
                    if (minWidth == 1) break;
                }
            }
        return best;
    }

    private int actualWidth(
            int x,
            int startZ,
            int endZ,
            int maximum,
            boolean[][] processed,
            BlockState[][] states,
            int type) {
        int width = maximum;
        for (int z = startZ; z <= endZ; z++) {
            int row = 0;
            for (int nextX = x; nextX < x + width; nextX++) {
                var state = states[nextX][z];
                if (processed[nextX][z]
                        || state == null
                        || !shapes.isMergeableShape(state)
                        || shapes.getShapeType(state) != type) break;
                row++;
            }
            width = Math.min(width, row);
            if (width <= 0) break;
        }
        return width;
    }

    private void mergeZDirectionRemains(
            BlockState[][] states, boolean[][] processed, List<MergedRect> merged) {
        for (int x = 0; x < 16; x++) {
            int z = 0;
            while (z < 16) {
                var state = states[x][z];
                if (processed[x][z] || state == null || !shapes.isMergeableShape(state)) {
                    z++;
                    continue;
                }
                int type = shapes.getShapeType(state), endZ = z;
                for (int next = z + 1; next < 16; next++) {
                    var candidate = states[x][next];
                    if (processed[x][next]
                            || candidate == null
                            || !shapes.isMergeableShape(candidate)
                            || shapes.getShapeType(candidate) != type) break;
                    endZ = next;
                }
                if (endZ == z) {
                    z++;
                    continue;
                }
                int width = 1;
                for (int nextX = x + 1; nextX < 16; nextX++) {
                    boolean extend = true;
                    for (int nextZ = z; nextZ <= endZ; nextZ++) {
                        var candidate = states[nextX][nextZ];
                        if (processed[nextX][nextZ]
                                || candidate == null
                                || !shapes.isMergeableShape(candidate)
                                || shapes.getShapeType(candidate) != type) {
                            extend = false;
                            break;
                        }
                    }
                    if (!extend) break;
                    width++;
                }
                merged.add(new MergedRect(x, z, x + width - 1, endZ, type, centerOffset(state)));
                markProcessed(processed, x, z, width, endZ - z + 1);
                z = endZ + 1;
            }
        }
    }

    private void markProcessed(boolean[][] processed, int x, int z, int width, int height) {
        for (int nextZ = z; nextZ < z + height; nextZ++)
            for (int nextX = x; nextX < x + width; nextX++)
                if (nextX < 16 && nextZ < 16) processed[nextX][nextZ] = true;
    }

    private float centerOffset(BlockState state) {
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return 0;
        var shape = shapes.getCollisionShape(state);
        return shape == shapes.getUP_HALF_BLOCK()
                ? -0.25F
                : shape == shapes.getDOWN_HALF_BLOCK() ? 0.25F : 0;
    }
}
