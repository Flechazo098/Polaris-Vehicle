// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.sparkcore.SparkCore;
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.PhysicsBodyExtension;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

import jme3utilities.math.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class PhysicsChunkSection implements PhysicsHost {
    public enum BuildState {
        IDLE,
        BUILDING,
        BUILT,
        FAILED
    }

    private record BuildRequest(long generation, SectionSnapshot snapshot) {}

    private final SectionPos sectionPos;
    private final PhysicsLevel physicsLevel;
    private final LevelChunk chunk;
    private final BlockMerger blockMerger;
    private final AtomicLong buildGeneration = new AtomicLong();
    private long committedGeneration = -1;
    private final Object buildLock = new Object();
    private boolean buildInFlight;
    private BuildRequest pendingBuild;
    private volatile boolean disposed, active, buildRequested;
    private volatile SectionSnapshot snapshotForCollision;
    private volatile int lastSlipEpoch = Integer.MIN_VALUE;
    private volatile CompoundCollisionShape collisionShape;
    private volatile PhysicsRigidBody physicsBody;
    private volatile BuildState buildState = BuildState.IDLE;
    private Future<?> buildJob;
    private final Map<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    public PhysicsChunkSection(SectionPos pos, PhysicsLevel level, LevelChunk chunk) {
        sectionPos = pos;
        physicsLevel = level;
        this.chunk = chunk;
        blockMerger = new BlockMerger(level);
    }

    public SectionPos getSectionPos() {
        return sectionPos;
    }

    @Override
    public PhysicsLevel getPhysicsLevel() {
        return physicsLevel;
    }

    @Override
    public Map<String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return allPhysicsBodies;
    }

    public PhysicsRigidBody getPhysicsBody() {
        return physicsBody;
    }

    public boolean isActive() {
        return active;
    }

    public boolean getBuildRequested() {
        return buildRequested;
    }

    public boolean isBuilding() {
        return buildState == BuildState.BUILDING;
    }

    public boolean isBuilt() {
        return physicsBody != null && collisionShape != null;
    }

    public boolean isEmpty() {
        return collisionShape == null;
    }

    public void requestBuild(PhysicsChunkManager manager) {
        if (buildRequested || disposed) return;
        buildRequested = true;
        startAsyncBuild(manager);
    }

    private CompoundCollisionShape buildCollisionShape(SectionSnapshot snapshot) {
        if (snapshot == SectionSnapshot.EMPTY) return null;
        var compound = new CompoundCollisionShape();
        boolean any = false;
        for (int y = 0; y < 16; y++) {
            var states = new BlockState[16][16];
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    var block = snapshot.getBlockSnapshot(x, y, z);
                    if (block != null) states[x][z] = block.state();
                }
            for (var rect : blockMerger.mergeLayer(y, states)) {
                compound.addChildShape(rect.toCollisionShape(), rect.getCenter(y));
                any = true;
            }
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    var block = snapshot.getBlockSnapshot(x, y, z);
                    if (block != null
                            && !physicsLevel
                                    .getBlockShapeManager()
                                    .isMergeableShape(block.state())) {
                        addComplexShape(
                                compound,
                                x,
                                y,
                                z,
                                physicsLevel
                                        .getBlockShapeManager()
                                        .getCollisionShape(block.state()));
                        any = true;
                    }
                }
        }
        return any ? compound : null;
    }

    private void addComplexShape(
            CompoundCollisionShape compound, int x, int y, int z, CollisionShape shape) {
        var offset = new Vector3f(x + 0.5F - 8, y + 0.5F - 8, z + 0.5F - 8);
        if (shape instanceof CompoundCollisionShape nested) {
            for (var child : nested.listChildren())
                compound.addChildShape(
                        child.getShape(),
                        MyMath.combine(
                                new Transform(offset, Quaternion.IDENTITY),
                                child.copyTransform(null),
                                null));
        } else compound.addChildShape(shape, new Transform(offset, Quaternion.IDENTITY));
    }

    public BlockState getBlockState(BlockPos worldPos) {
        var block = getBlockSnapshot(worldPos);
        return block == null ? null : block.state();
    }

    public SectionSnapshot.BlockSnapshot getBlockSnapshot(BlockPos pos) {
        var snapshot = snapshotForCollision;
        return isEmpty() || snapshot == null || snapshot.isEmpty()
                ? null
                : snapshot.getBlockSnapshotFromWorld(pos);
    }

    public BlockPos getBlockPosFromContactPoint(Vector3f point, Vector3f normal, float distance) {
        var pos = point.add(normal.mult(distance));
        return new BlockPos(
                (int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
    }

    public void markRemoved(BlockPos pos) {
        var snapshot = snapshotForCollision;
        if (isEmpty() || snapshot == null || snapshot.isEmpty() || isRemoved(pos)) return;
        var relative = snapshot.getRelativePos(pos);
        snapshot.getBlockSnapshots()[
                        SectionSnapshot.getIndexFromRelativePos(
                                relative.getX(), relative.getY(), relative.getZ())] =
                null;
    }

    public boolean isRemoved(BlockPos pos) {
        return getBlockSnapshot(pos) == null;
    }

    public void refreshSlipIfNeeded(int epoch) {
        if (lastSlipEpoch == epoch) return;
        var snapshot = snapshotForCollision;
        if (snapshot != null && !snapshot.isEmpty()) {
            var blocks = snapshot.getBlockSnapshots();
            for (int i = 0; i < blocks.length; i++) {
                var block = blocks[i];
                if (block == null) continue;
                float slip =
                        BlockCollisionUtil.getSlip(
                                chunk,
                                block.state(),
                                snapshot.getWorldPos(SectionSnapshot.getRelativePosFromIndex(i)));
                if (slip != block.slip()) blocks[i] = block.withSlip(slip);
            }
        }
        lastSlipEpoch = epoch;
    }

    private void replaceCollisionSnapshot(SectionSnapshot snapshot) {
        snapshotForCollision = snapshot == null ? null : snapshot.copy();
        lastSlipEpoch = Integer.MIN_VALUE;
    }

    public void startAsyncBuild(PhysicsChunkManager manager) {
        scheduleBuild(manager);
    }

    public void startAsyncUpdate(PhysicsChunkManager manager) {
        scheduleBuild(manager);
    }

    private void scheduleBuild(PhysicsChunkManager manager) {
        if (disposed) return;
        var snapshot =
                SectionSnapshot.snapshotFromChunk(physicsLevel.getMcLevel(), chunk, sectionPos);
        synchronized (buildLock) {
            if (disposed) return;
            pendingBuild = new BuildRequest(buildGeneration.incrementAndGet(), snapshot);
            if (buildInFlight) return;
            buildInFlight = true;
            buildState = BuildState.BUILDING;
            try {
                buildJob = manager.submitBuild(this::runBuilds);
            } catch (RejectedExecutionException failure) {
                buildInFlight = false;
                buildState = BuildState.FAILED;
                throw failure;
            }
        }
    }

    private void runBuilds() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                BuildRequest request;
                synchronized (buildLock) {
                    request = pendingBuild;
                    pendingBuild = null;
                    if (request == null) {
                        buildInFlight = false;
                        return;
                    }
                }
                var shape = buildCollisionShape(request.snapshot());
                if (Thread.currentThread().isInterrupted()) return;
                physicsLevel.submitImmediateTask(
                        PPhase.PRE,
                        () -> {
                            if (!disposed && request.generation() > committedGeneration) {
                                commitBuild(request.snapshot(), shape);
                                committedGeneration = request.generation();
                                buildState = shape != null ? BuildState.BUILT : BuildState.IDLE;
                            }
                        });
            }
        } catch (Exception failure) {
            if (!disposed) buildState = BuildState.FAILED;
            SparkCore.LOGGER.error("Building section " + sectionPos + " failed", failure);
            synchronized (buildLock) {
                buildInFlight = false;
            }
        } catch (Error failure) {
            buildState = BuildState.FAILED;
            synchronized (buildLock) {
                buildInFlight = false;
            }
            physicsLevel.deferFailure(failure);
        }
    }

    private void commitBuild(SectionSnapshot snapshot, CompoundCollisionShape shape) {
        var body = physicsBody;
        if (body != null && physicsLevel.getWorld().contains(body))
            physicsLevel.getWorld().removeCollisionObject(body);
        if (shape == null) {
            physicsBody = null;
            collisionShape = null;
            replaceCollisionSnapshot(snapshot);
            return;
        }
        if (body == null) {
            body = new PhysicsRigidBody(shape, 0);
            body.name = "section_" + sectionPos;
            PhysicsBodyExtension.setOwner(body, this);
            body.setPhysicsLocation(
                    new Vector3f(
                            sectionPos.minBlockX() + 8F,
                            sectionPos.minBlockY() + 8F,
                            sectionPos.minBlockZ() + 8F));
            body.setCollisionGroup(CollisionGroups.TERRAIN);
            body.setCollideWithGroups(CollisionGroups.NONE);
        } else body.setCollisionShape(shape);
        collisionShape = shape;
        physicsBody = body;
        replaceCollisionSnapshot(snapshot);
        if (active && !disposed) physicsLevel.getWorld().addCollisionObject(body);
    }

    public void cancelBuild() {
        synchronized (buildLock) {
            committedGeneration = buildGeneration.incrementAndGet();
            pendingBuild = null;
            if (buildJob != null) buildJob.cancel(true);
            buildJob = null;
            buildInFlight = false;
        }
        if (buildState == BuildState.BUILDING) buildState = BuildState.IDLE;
    }

    public void activate() {
        if (disposed || active) return;
        active = true;
        refreshSlipIfNeeded(physicsLevel.getTerrainManager().currentWeatherEpoch());
        physicsLevel.submitImmediateTask(
                PPhase.PRE,
                () -> {
                    var body = physicsBody;
                    if (active
                            && !disposed
                            && body != null
                            && !physicsLevel.getWorld().contains(body))
                        physicsLevel.getWorld().addCollisionObject(body);
                });
    }

    public void deactivate() {
        if (!active) return;
        active = false;
        physicsLevel.submitImmediateTask(
                PPhase.PRE,
                () -> {
                    var body = physicsBody;
                    if (!active && body != null && physicsLevel.getWorld().contains(body))
                        physicsLevel.getWorld().removeCollisionObject(body);
                });
    }

    public void destroy() {
        disposed = true;
        active = false;
        cancelBuild();
        physicsLevel.submitImmediateTask(
                PPhase.PRE,
                () -> {
                    var body = physicsBody;
                    if (body != null && physicsLevel.getWorld().contains(body))
                        physicsLevel.getWorld().removeCollisionObject(body);
                    physicsBody = null;
                    collisionShape = null;
                    snapshotForCollision = null;
                    lastSlipEpoch = Integer.MIN_VALUE;
                    allPhysicsBodies.clear();
                });
    }
}
