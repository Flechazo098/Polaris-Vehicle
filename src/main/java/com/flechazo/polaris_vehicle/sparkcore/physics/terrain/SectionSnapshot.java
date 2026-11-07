// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SectionSnapshot {
    public static final SectionSnapshot EMPTY =
            new SectionSnapshot(new BlockSnapshot[1], SectionPos.of(0, Integer.MIN_VALUE, 0));
    private final BlockSnapshot[] blockSnapshots;
    private final SectionPos pos;

    private SectionSnapshot(BlockSnapshot[] blocks, SectionPos pos) {
        blockSnapshots = blocks;
        this.pos = pos;
    }

    public BlockSnapshot[] getBlockSnapshots() {
        return blockSnapshots;
    }

    public SectionPos getPos() {
        return pos;
    }

    public static SectionSnapshot snapshotFromChunk(Level level, LevelChunk chunk, SectionPos pos) {
        int index = level.getSectionIndexFromSectionY(pos.y());
        if (index < 0 || index >= chunk.getSections().length) return EMPTY;
        var section = chunk.getSections()[index];
        if (section == null || section.hasOnlyAir()) return EMPTY;
        var blocks = new BlockSnapshot[4096];
        boolean any = false;
        for (int x = 0; x < 16; x++)
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    if (!state.isAir()
                            && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                    .isEmpty()) {
                        var worldPos = getWorldPos(pos, x, y, z);
                        var data = BlockCollisionUtil.getPhysicsData(state);
                        blocks[getIndexFromRelativePos(x, y, z)] =
                                new BlockSnapshot(
                                        state,
                                        data.friction(),
                                        data.rollingFriction(),
                                        data.restitution(),
                                        BlockCollisionUtil.calcSlip(data, chunk, worldPos));
                        any = true;
                    }
                }
        return any ? new SectionSnapshot(blocks, pos) : EMPTY;
    }

    public static int getIndexFromRelativePos(int x, int y, int z) {
        if (x < 0 || x > 15 || y < 0 || y > 15 || z < 0 || z > 15)
            throw new IllegalArgumentException(
                    "Coordinates must be in range 0-15, got x=" + x + ", y=" + y + ", z=" + z);
        return x + (y << 4) + (z << 8);
    }

    public static BlockPos getRelativePosFromIndex(int index) {
        if (index < 0 || index > 4095)
            throw new IllegalArgumentException("Index must be in range 0-4095, got " + index);
        return new BlockPos(index & 15, (index >> 4) & 15, (index >> 8) & 15);
    }

    public static BlockPos getWorldPos(SectionPos pos, int x, int y, int z) {
        return pos.origin().offset(x, y, z);
    }

    public record BlockSnapshot(
            BlockState state,
            float friction,
            float rollingFriction,
            float restitution,
            float slip) {
        public BlockState getState() {
            return state;
        }

        public float getFriction() {
            return friction;
        }

        public float getRollingFriction() {
            return rollingFriction;
        }

        public float getRestitution() {
            return restitution;
        }

        public float getSlip() {
            return slip;
        }

        public BlockSnapshot withSlip(float value) {
            return new BlockSnapshot(state, friction, rollingFriction, restitution, value);
        }
    }

    public BlockPos getRelativePos(BlockPos world) {
        var origin = pos.origin();
        return new BlockPos(
                Math.clamp(world.getX() - origin.getX(), 0, 15),
                Math.clamp(world.getY() - origin.getY(), 0, 15),
                Math.clamp(world.getZ() - origin.getZ(), 0, 15));
    }

    public BlockPos getWorldPos(BlockPos relative) {
        return relative.offset(pos.origin());
    }

    public BlockSnapshot getBlockSnapshot(int x, int y, int z) {
        return blockSnapshots[getIndexFromRelativePos(x, y, z)];
    }

    public BlockSnapshot getBlockSnapshot(BlockPos relative) {
        return getBlockSnapshot(relative.getX(), relative.getY(), relative.getZ());
    }

    public BlockSnapshot getBlockSnapshotFromWorld(BlockPos world) {
        if (isEmpty()
                || world.getX() >> 4 != pos.x()
                || world.getY() >> 4 != pos.y()
                || world.getZ() >> 4 != pos.z()) return null;
        return getBlockSnapshot(getRelativePos(world));
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public int getBlockCount() {
        int count = 0;
        if (!isEmpty()) for (var block : blockSnapshots) if (block != null) count++;
        return count;
    }

    public SectionSnapshot copy() {
        return isEmpty() ? EMPTY : new SectionSnapshot(blockSnapshots.clone(), pos);
    }
}
