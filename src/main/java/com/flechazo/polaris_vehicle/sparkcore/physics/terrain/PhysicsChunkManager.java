// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.hkt.business.concurrent.ExecutorResources;
import com.flechazo.hkt.business.concurrent.ExecutorShutdownPolicy;
import com.flechazo.hkt.business.concurrent.ThreadConfig;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.effect.ExitCase;
import com.flechazo.hkt.business.effect.Resource;
import com.flechazo.hkt.business.util.ThrowableSupport;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhysicsChunkManager implements PhysicsCollisionListener {
    private static final long IDLE_CHUNK_TICKS = 600;
    public static final TicketType<ChunkPos> SPARK_TERRAIN_TICKET =
            TicketType.create("spark_terrain", Comparator.comparingLong(ChunkPos::toLong), 1200);
    private final PhysicsLevel physicsLevel;
    private final Map<ChunkPos, PhysicsChunk> loadedChunks = new HashMap<>();
    private final Map<ChunkPos, LevelChunk> mcLoadedChunks = new HashMap<>();
    private final Set<SectionPos> dirtySections = ConcurrentHashMap.newKeySet();
    private final Resource.Allocation<ExecutorService> terrainBuilder;
    private volatile Thread terrainBuilderThread;
    private boolean destroyed;
    private volatile Throwable destroyFailure;
    private final ChunkHeightIndex chunkHeightIndex = new ChunkHeightIndex();
    private final ConcurrentHashMap<ChunkPos, Integer> chunkExpireTicks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<SectionRange>> terrainActivationRanges =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkPos, Set<SectionRange>> pendingBuildRequests =
            new ConcurrentHashMap<>();
    private final PhysicsGhostObject activator =
            new PhysicsGhostObject(new SphereCollisionShape(2F));
    private final AtomicInteger weatherEpoch = new AtomicInteger();
    private boolean raining;

    public PhysicsChunkManager(PhysicsLevel level) {
        physicsLevel = level;
        raining = level.getMcLevel().isRaining();
        activator.addCollideWithGroup(CollisionGroups.PHYSICS_BODY);
        terrainBuilder =
                ExecutorResources.fixed(
                                1,
                                ThreadConfig.platform("TerrainShapeBuilder-" + level.getName()),
                                ExecutorShutdownPolicy.defaults())
                        .allocate()
                        .unsafeRun();
    }

    public synchronized Future<?> submitBuild(Runnable build) {
        if (destroyed)
            throw new java.util.concurrent.RejectedExecutionException("Terrain builder is closed");
        return terrainBuilder
                .value()
                .submit(
                        () -> {
                            terrainBuilderThread = Thread.currentThread();
                            build.run();
                        });
    }

    public boolean isBuilderTerminated() {
        return terrainBuilder.value().isTerminated();
    }

    public ChunkHeightIndex getChunkHeightIndex() {
        return chunkHeightIndex;
    }

    public Map<ChunkPos, PhysicsChunk> getLoadedChunks() {
        return loadedChunks;
    }

    public int currentWeatherEpoch() {
        return weatherEpoch.get();
    }

    public void updateWeatherSlipIfNeeded() {
        boolean next = physicsLevel.getMcLevel().isRaining();
        if (next == raining) return;
        raining = next;
        int epoch = weatherEpoch.incrementAndGet();
        for (var chunk : loadedChunks.values())
            for (int y = physicsLevel.getMcLevel().getMinSection();
                    y < physicsLevel.getMcLevel().getMaxSection();
                    y++) {
                var section = chunk.getSection(y);
                if (section != null && section.isActive()) section.refreshSlipIfNeeded(epoch);
            }
    }

    public void unloadPhysicsChunk(ChunkPos pos) {
        var chunk = loadedChunks.get(pos);
        if (chunk == null) return;
        dirtySections.removeIf(section -> section.x() == pos.x && section.z() == pos.z);
        chunk.unload();
        loadedChunks.remove(pos);
    }

    public void updateBuild(List<AABB> boxes) {
        var build = new HashMap<ChunkPos, Set<SectionRange>>();
        var level = physicsLevel.getMcLevel();
        for (var box : boxes) {
            int minY = SectionPos.blockToSectionCoord((int) box.minY),
                    maxY = SectionPos.blockToSectionCoord((int) box.maxY);
            if (minY > level.getMaxSection() + 1 || maxY < level.getMinSection() - 1) continue;
            var range =
                    new SectionRange(
                            SectionPos.blockToSectionCoord((int) (box.minY - 8)),
                            SectionPos.blockToSectionCoord((int) (box.maxY + 8)));
            for (int x = SectionPos.blockToSectionCoord((int) box.minX) - 2;
                    x <= SectionPos.blockToSectionCoord((int) box.maxX) + 2;
                    x++)
                for (int z = SectionPos.blockToSectionCoord((int) box.minZ) - 2;
                        z <= SectionPos.blockToSectionCoord((int) box.maxZ) + 2;
                        z++) {
                    var pos = new ChunkPos(x, z);
                    if (!mcLoadedChunks.containsKey(pos) && level instanceof ServerLevel server) {
                        var chunk = server.getChunkSource().getChunkNow(x, z);
                        if (chunk != null) onChunkLoaded(pos, chunk);
                    }
                    if (mcLoadedChunks.containsKey(pos))
                        build.computeIfAbsent(pos, ignored -> new HashSet<>()).add(range);
                }
        }
        long now = level.getGameTime();
        build.forEach(
                (pos, ranges) -> {
                    var chunk = ensureChunk(pos, mcLoadedChunks.get(pos));
                    if (chunk != null) {
                        chunk.setLastUsedTick(now);
                        chunk.requestBuild(mergeRanges(ranges));
                    }
                });
        if (now % 100 == 0) {
            var idle =
                    Pathway.listPath(new ArrayList<>(loadedChunks.entrySet()))
                            .filter(
                                    entry ->
                                            now - entry.getValue().getLastUsedTick()
                                                            > IDLE_CHUNK_TICKS
                                                    && terrainActivationRanges
                                                            .getOrDefault(entry.getKey(), Set.of())
                                                            .isEmpty()
                                                    && !pendingBuildRequests.containsKey(
                                                            entry.getKey()))
                            .map(Map.Entry::getKey)
                            .run();
            idle.forEach(this::unloadPhysicsChunk);
        }
    }

    private PhysicsChunk ensureChunk(ChunkPos pos, LevelChunk mcChunk) {
        var existing = loadedChunks.get(pos);
        if (existing != null) return existing;
        if (mcChunk == null) return null;
        var chunk = new PhysicsChunk(pos, physicsLevel, mcChunk);
        chunk.load();
        loadedChunks.put(pos, chunk);
        return chunk;
    }

    public SectionSnapshot.BlockSnapshot getBlockSnapshotAt(BlockPos pos) {
        var section = getSectionForBlockPos(pos);
        return section == null ? null : section.getBlockSnapshot(pos);
    }

    public void updateActivation(List<AABB> boxes) {
        var activation = new HashMap<ChunkPos, Set<SectionRange>>();
        var level = physicsLevel.getMcLevel();
        for (var box : boxes) {
            int minY = SectionPos.blockToSectionCoord((int) (box.minY - 2)),
                    maxY = SectionPos.blockToSectionCoord((int) (box.maxY + 2));
            if (minY > level.getMaxSection() || maxY < level.getMinSection()) continue;
            var range = new SectionRange(minY, maxY);
            for (int x = SectionPos.blockToSectionCoord((int) (box.minX - 2));
                    x <= SectionPos.blockToSectionCoord((int) (box.maxX + 2));
                    x++)
                for (int z = SectionPos.blockToSectionCoord((int) (box.minZ - 2));
                        z <= SectionPos.blockToSectionCoord((int) (box.maxZ + 2));
                        z++)
                    activation
                            .computeIfAbsent(new ChunkPos(x, z), ignored -> new HashSet<>())
                            .add(range);
        }
        loadedChunks.forEach(
                (pos, chunk) -> {
                    var ranges = new HashSet<>(activation.getOrDefault(pos, Set.of()));
                    ranges.addAll(terrainActivationRanges.getOrDefault(pos, Set.of()));
                    if (ranges.isEmpty()) chunk.deactivateAll();
                    else chunk.activateSectionsInRanges(mergeRanges(ranges));
                });
    }

    private List<SectionRange> mergeRanges(Set<SectionRange> ranges) {
        if (ranges.isEmpty()) return List.of();
        var sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(SectionRange::first));
        var merged = new ArrayList<SectionRange>();
        var current = sorted.getFirst();
        for (int i = 1; i < sorted.size(); i++) {
            var next = sorted.get(i);
            if (next.first() <= current.last() + 1)
                current = new SectionRange(current.first(), Math.max(current.last(), next.last()));
            else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    public PhysicsChunkSection getSectionForBlockPos(BlockPos pos) {
        var chunk = loadedChunks.get(new ChunkPos(pos));
        return chunk == null ? null : chunk.getSectionForBlockPos(pos);
    }

    public void onChunkLoaded(ChunkPos pos, LevelChunk chunk) {
        if (mcLoadedChunks.putIfAbsent(pos, chunk) == null)
            chunkHeightIndex.computeAndPut(chunk, physicsLevel.getMcLevel());
    }

    public void onChunkUnloaded(ChunkPos pos) {
        mcLoadedChunks.remove(pos);
        unloadPhysicsChunk(pos);
    }

    public void onBlockUpdated(Set<BlockPos> positions) {
        var sections = new HashSet<SectionPos>();
        var affected = new HashMap<ChunkPos, Set<Integer>>();
        for (var pos : positions) {
            var section = SectionPos.of(pos);
            sections.add(section);
            affected.computeIfAbsent(new ChunkPos(pos), ignored -> new HashSet<>())
                    .add(section.y());
            physicsLevel.submitDeduplicatedTask(
                    "terrain_update_activate_" + pos.toShortString(),
                    PPhase.ALL,
                    () -> {
                        activator.setPhysicsLocation(
                                new Vector3f(
                                        pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F));
                        physicsLevel.getWorld().contactTest(activator, this);
                    });
        }
        markDirtySections(sections);
        affected.forEach(
                (pos, ys) -> {
                    var chunk = mcLoadedChunks.get(pos);
                    if (chunk != null)
                        for (int y : ys)
                            chunkHeightIndex.updateSection(
                                    pos, chunk, physicsLevel.getMcLevel(), y);
                });
    }

    public void scheduleTerrain(ChunkPos pos, SectionRange range, int holdTicks) {
        chunkExpireTicks.merge(pos, physicsLevel.getTickCount() + holdTicks, Math::max);
        terrainActivationRanges
                .computeIfAbsent(pos, ignored -> ConcurrentHashMap.newKeySet())
                .add(range);
        if (!(physicsLevel.getMcLevel() instanceof ServerLevel server)) return;
        server.getChunkSource().addRegionTicket(SPARK_TERRAIN_TICKET, pos, 2, pos, true);
        pendingBuildRequests
                .computeIfAbsent(pos, ignored -> ConcurrentHashMap.newKeySet())
                .add(range);
    }

    public void cancelAllSchedules(ChunkPos pos) {
        releaseChunkTerrain(pos);
    }

    private void releaseChunkTerrain(ChunkPos pos) {
        if (physicsLevel.getMcLevel() instanceof ServerLevel server)
            server.getChunkSource().removeRegionTicket(SPARK_TERRAIN_TICKET, pos, 2, pos);
        chunkExpireTicks.remove(pos);
        terrainActivationRanges.remove(pos);
        pendingBuildRequests.remove(pos);
    }

    public boolean isTerrainReady(ChunkPos pos, SectionRange range) {
        var chunk = loadedChunks.get(pos);
        if (chunk == null) return false;
        for (int y = range.first(); y <= range.last(); y++) {
            var section = chunk.getSection(y);
            if (section != null && (!section.isActive() || !section.isBuilt())) return false;
        }
        return true;
    }

    public void processPendingBuildRequests() {
        if (pendingBuildRequests.isEmpty()) return;
        var requests = new HashMap<>(pendingBuildRequests);
        pendingBuildRequests.clear();
        requests.forEach(
                (pos, ranges) -> {
                    for (var range : ranges) loadAndActivateChunkTerrain(pos, range);
                });
    }

    public void updateScheduledChunks() {
        int now = physicsLevel.getTickCount();
        var expired =
                Pathway.listPath(new ArrayList<>(chunkExpireTicks.entrySet()))
                        .filter(entry -> now >= entry.getValue())
                        .map(Map.Entry::getKey)
                        .run();
        expired.forEach(this::releaseChunkTerrain);
    }

    private void loadAndActivateChunkTerrain(ChunkPos pos, SectionRange range) {
        if (!(physicsLevel.getMcLevel() instanceof ServerLevel server)) return;
        var mcChunk = server.getChunk(pos.x, pos.z);
        onChunkLoaded(pos, mcChunk);
        var chunk = ensureChunk(pos, mcChunk);
        chunk.setLastUsedTick(server.getGameTime());
        chunk.activateSectionsInRanges(List.of(range));
    }

    public void markDirtySections(Set<SectionPos> sections) {
        dirtySections.addAll(sections);
    }

    public void updateDirtySections() {
        var sections = Set.copyOf(dirtySections);
        dirtySections.removeAll(sections);
        for (var pos : sections) {
            var chunk = loadedChunks.get(new ChunkPos(pos.x(), pos.z()));
            if (chunk == null) continue;
            var section = chunk.getSection(pos.y());
            if (section != null && section.getBuildRequested()) section.startAsyncUpdate(this);
        }
    }

    public boolean loaded(ChunkPos pos) {
        return loadedChunks.containsKey(pos);
    }

    public String getStats() {
        int total = 0, active = 0;
        for (var chunk : loadedChunks.values()) {
            total += chunk.getTotalSectionCount();
            active += chunk.getActiveSectionCount();
        }
        return "物理区块: "
                + loadedChunks.size()
                + ", Section总数: "
                + total
                + ", 活跃Section: "
                + active
                + ", 索引Chunk数: "
                + chunkHeightIndex.getSize();
    }

    public void destroy() {
        destroy(ExitCase.succeeded());
    }

    public void destroy(ExitCase exit) {
        if (Thread.currentThread() == terrainBuilderThread)
            throw new IllegalStateException(
                    "Terrain builder cannot synchronously close its own executor");
        synchronized (this) {
            if (destroyed) {
                if (destroyFailure != null) throw ThrowableSupport.propagate(destroyFailure);
                return;
            }
            destroyed = true;
        }
        Throwable failure = null;
        for (var pos : Set.copyOf(chunkExpireTicks.keySet())) {
            try {
                releaseChunkTerrain(pos);
            } catch (Throwable error) {
                failure = failure == null ? error : ThrowableSupport.suppress(failure, error);
            }
        }
        for (var chunk : loadedChunks.values()) {
            try {
                chunk.unload();
            } catch (Throwable error) {
                failure = failure == null ? error : ThrowableSupport.suppress(failure, error);
            }
        }
        if (exit instanceof ExitCase.Succeeded && failure != null) {
            var cause = ThrowableSupport.unwrapAsync(failure);
            exit =
                    ThrowableSupport.isCancellation(cause)
                            ? ExitCase.canceled(cause)
                            : ExitCase.failed(cause);
        }
        try {
            terrainBuilder.release(exit).execute();
        } catch (Throwable error) {
            failure = failure == null ? error : ThrowableSupport.suppress(failure, error);
        } finally {
            terrainBuilderThread = null;
            loadedChunks.clear();
            chunkExpireTicks.clear();
            terrainActivationRanges.clear();
            pendingBuildRequests.clear();
        }
        if (failure != null) {
            destroyFailure = failure;
            throw ThrowableSupport.propagate(failure);
        }
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {
        var a = event.getObjectA();
        var b = event.getObjectB();
        PhysicsRigidBody body =
                a == activator && b instanceof PhysicsRigidBody rigid
                        ? rigid
                        : b == activator && a instanceof PhysicsRigidBody rigid ? rigid : null;
        if (body != null && body.isDynamic() && !body.isActive()) body.activate();
    }
}
