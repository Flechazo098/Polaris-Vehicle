// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkHeightIndex {
    private final ConcurrentHashMap<Long, short[]> intervals = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Long, short[]> getIntervals() {
        return intervals;
    }

    public int getSize() {
        return intervals.size();
    }

    public boolean hasSolidInRange(ChunkPos pos, short min, short max) {
        var array = intervals.get(pos.toLong());
        if (array == null) return false;
        int lo = 0, hi = array.length / 2 - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (max < array[mid * 2]) hi = mid - 1;
            else if (min > array[mid * 2 + 1]) lo = mid + 1;
            else return true;
        }
        return false;
    }

    public short[] getIntervals(ChunkPos pos) {
        return intervals.get(pos.toLong());
    }

    public boolean hasChunk(ChunkPos pos) {
        return intervals.containsKey(pos.toLong());
    }

    public void computeAndPut(LevelChunk chunk, Level level) {
        var ranges = new ArrayList<SectionRange>();
        int start = Integer.MIN_VALUE;
        for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
            boolean solid = sectionHasSolidBlock(chunk, level, y);
            if (solid && start == Integer.MIN_VALUE) start = y << 4;
            else if (!solid && start != Integer.MIN_VALUE) {
                ranges.add(new SectionRange(start, (y << 4) - 1));
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE)
            ranges.add(new SectionRange(start, (level.getMaxSection() << 4) - 1));
        intervals.put(chunk.getPos().toLong(), encode(ranges));
    }

    public void updateSection(ChunkPos pos, LevelChunk chunk, Level level, int sectionY) {
        var existing = intervals.get(pos.toLong());
        if (existing == null) {
            computeAndPut(chunk, level);
            return;
        }
        int min = (short) (sectionY << 4), max = (short) ((sectionY << 4) + 15), cover = -1;
        for (int i = 0; i < existing.length; i += 2)
            if (min >= existing[i] && max <= existing[i + 1]) {
                cover = i;
                break;
            }
        boolean solid = sectionHasSolidBlock(chunk, level, sectionY);
        if (solid == (cover >= 0)) return;
        var ranges = new ArrayList<SectionRange>();
        for (int i = 0; i < existing.length; i += 2) {
            if (i == cover) {
                if (existing[i] < min) ranges.add(new SectionRange(existing[i], min - 1));
                if (existing[i + 1] > max) ranges.add(new SectionRange(max + 1, existing[i + 1]));
            } else ranges.add(new SectionRange(existing[i], existing[i + 1]));
        }
        if (solid) {
            ranges.add(new SectionRange(min, max));
            ranges.sort(Comparator.comparingInt(SectionRange::first));
            var merged = new ArrayList<SectionRange>();
            var current = ranges.getFirst();
            for (int i = 1; i < ranges.size(); i++) {
                var next = ranges.get(i);
                if (next.first() <= current.last() + 1)
                    current =
                            new SectionRange(
                                    current.first(), Math.max(current.last(), next.last()));
                else {
                    merged.add(current);
                    current = next;
                }
            }
            merged.add(current);
            ranges = merged;
        }
        intervals.put(pos.toLong(), encode(ranges));
    }

    private short[] encode(List<SectionRange> ranges) {
        var array = new short[ranges.size() * 2];
        for (int i = 0; i < ranges.size(); i++) {
            array[2 * i] = (short) ranges.get(i).first();
            array[2 * i + 1] = (short) ranges.get(i).last();
        }
        return array;
    }

    private boolean sectionHasSolidBlock(LevelChunk chunk, Level level, int y) {
        int index = level.getSectionIndexFromSectionY(y);
        if (index < 0
                || index >= chunk.getSections().length
                || chunk.getSections()[index].hasOnlyAir()) return false;
        for (int dy = 0; dy < 16; dy++)
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    if (BlockCollisionUtil.hasCollision(
                            chunk.getBlockState(new BlockPos(x, (y << 4) + dy, z)))) return true;
        return false;
    }

    public Map<Long, short[]> toPersistentMap() {
        return new ConcurrentHashMap<>(intervals);
    }

    public void loadFromPersistentMap(Map<Long, short[]> data) {
        intervals.clear();
        intervals.putAll(data);
    }
}
