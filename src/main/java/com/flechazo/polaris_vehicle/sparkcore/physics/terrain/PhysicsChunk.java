// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PhysicsChunk {
    private final ChunkPos chunkPos;
    private final PhysicsLevel physicsLevel;
    private final LevelChunk chunk;
    private final Map<Integer, PhysicsChunkSection> sections = new HashMap<>();
    private boolean loaded, mayHaveActive;
    private volatile long lastUsedTick;

    public PhysicsChunk(ChunkPos pos, PhysicsLevel level, LevelChunk chunk) {
        chunkPos = pos;
        physicsLevel = level;
        this.chunk = chunk;
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public long getLastUsedTick() {
        return lastUsedTick;
    }

    public void setLastUsedTick(long value) {
        lastUsedTick = value;
    }

    public void load() {
        if (loaded) return;
        for (int y = physicsLevel.getMcLevel().getMinSection();
                y < physicsLevel.getMcLevel().getMaxSection();
                y++)
            sections.put(
                    y, new PhysicsChunkSection(SectionPos.of(chunkPos, y), physicsLevel, chunk));
        loaded = true;
    }

    public void unload() {
        sections.values().forEach(PhysicsChunkSection::destroy);
        sections.clear();
        loaded = false;
    }

    private boolean contains(List<SectionRange> ranges, int y) {
        for (var range : ranges) if (range.contains(y)) return true;
        return false;
    }

    public void activateSectionsInRanges(List<SectionRange> ranges) {
        mayHaveActive = true;
        sections.forEach(
                (y, section) -> {
                    if (contains(ranges, y)) {
                        section.requestBuild(physicsLevel.getTerrainManager());
                        section.activate();
                    } else if (section.isActive()) section.deactivate();
                });
    }

    public void requestBuild(List<SectionRange> ranges) {
        sections.forEach(
                (y, section) -> {
                    if (contains(ranges, y)) section.requestBuild(physicsLevel.getTerrainManager());
                });
    }

    public void deactivateSectionsInRanges(List<SectionRange> ranges) {
        sections.forEach(
                (y, section) -> {
                    if (contains(ranges, y) && section.isActive()) section.deactivate();
                });
    }

    public void activateAll() {
        mayHaveActive = true;
        sections.values().forEach(PhysicsChunkSection::activate);
    }

    public void deactivateAll() {
        if (!mayHaveActive) return;
        sections.values().forEach(PhysicsChunkSection::deactivate);
        mayHaveActive = false;
    }

    public PhysicsChunkSection getSection(int y) {
        return sections.get(y);
    }

    public PhysicsChunkSection getSectionForBlockPos(BlockPos pos) {
        return sections.get(SectionPos.blockToSectionCoord(pos.getY()));
    }

    public int getActiveSectionCount() {
        int count = 0;
        for (var section : sections.values()) if (section.isActive() && !section.isEmpty()) count++;
        return count;
    }

    public int getTotalSectionCount() {
        int count = 0;
        for (var section : sections.values()) if (section.getBuildRequested()) count++;
        return count;
    }
}
