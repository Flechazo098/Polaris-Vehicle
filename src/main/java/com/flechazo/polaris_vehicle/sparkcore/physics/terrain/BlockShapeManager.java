// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.flechazo.polaris_vehicle.api.collision.BlockBulletShapes;
import com.flechazo.polaris_vehicle.api.collision.BlockModelCollisions;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockShapeManager {
    private final PhysicsLevel physicsLevel;
    private final Map<BlockState, CollisionShape> SHAPE_CACHE = new ConcurrentHashMap<>();
    private final Map<BlockState, List<AABB>> AABB_CACHE = new ConcurrentHashMap<>();
    private final BoxCollisionShape FULL_BLOCK = new BoxCollisionShape(0.5F);
    private final BoxCollisionShape UP_HALF_BLOCK = new BoxCollisionShape(0.5F, 0.25F, 0.5F);
    private final BoxCollisionShape DOWN_HALF_BLOCK = new BoxCollisionShape(0.5F, 0.25F, 0.5F);

    public BlockShapeManager(PhysicsLevel level) {
        physicsLevel = level;
    }

    public Map<BlockState, CollisionShape> getSHAPE_CACHE() {
        return SHAPE_CACHE;
    }

    public Map<BlockState, List<AABB>> getAABB_CACHE() {
        return AABB_CACHE;
    }

    public BoxCollisionShape getFULL_BLOCK() {
        return FULL_BLOCK;
    }

    public BoxCollisionShape getUP_HALF_BLOCK() {
        return UP_HALF_BLOCK;
    }

    public BoxCollisionShape getDOWN_HALF_BLOCK() {
        return DOWN_HALF_BLOCK;
    }

    public CollisionShape getCollisionShape(BlockState state) {
        return SHAPE_CACHE.computeIfAbsent(
                state,
                key -> {
                    var shape =
                            BlockModelCollisions.find(key)
                                    .map(model -> (CollisionShape) BlockBulletShapes.create(model))
                                    .orElseGet(() -> convertVoxelToCollisionShape(key));
                    getBlockAabbs(key);
                    return shape;
                });
    }

    public List<AABB> getBlockAabbs(BlockState state) {
        return AABB_CACHE.computeIfAbsent(
                state,
                key ->
                        key.getCollisionShape(
                                        EmptyBlockGetter.INSTANCE,
                                        BlockPos.ZERO,
                                        CollisionContext.empty())
                                .toAabbs());
    }

    private CollisionShape convertVoxelToCollisionShape(BlockState state) {
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return FULL_BLOCK;
        var voxel =
                state.getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        if (isXZFullShape(state)) {
            if (voxel.min(Direction.Axis.Y) == 0 && voxel.max(Direction.Axis.Y) == 0.5)
                return UP_HALF_BLOCK;
            if (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1)
                return DOWN_HALF_BLOCK;
        }
        try {
            var bounds = voxel.bounds();
            var center = bounds.getCenter();
            var box =
                    new BoxCollisionShape(
                            new Vector3f(
                                    (float) (bounds.getXsize() / 2),
                                    (float) (bounds.getYsize() / 2),
                                    (float) (bounds.getZsize() / 2)));
            if (center.x == 0.5 && center.y == 0.5 && center.z == 0.5) return box;
            var compound = new CompoundCollisionShape();
            compound.addChildShape(
                    box,
                    new Transform(
                            new Vector3f(
                                    (float) center.x - 0.5F,
                                    (float) center.y - 0.5F,
                                    (float) center.z - 0.5F),
                            Quaternion.IDENTITY));
            return compound;
        } catch (Exception error) {
            return FULL_BLOCK;
        }
    }

    public boolean isXZFullShape(BlockState state) {
        var voxel =
                state.getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        return voxel.min(Direction.Axis.X) == 0
                && voxel.min(Direction.Axis.Z) == 0
                && voxel.max(Direction.Axis.X) == 1
                && voxel.max(Direction.Axis.Z) == 1;
    }

    public boolean isMergeableShape(BlockState state) {
        if (BlockModelCollisions.find(state).isDefined()) return false;
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return true;
        if (!isXZFullShape(state)) return false;
        var voxel =
                state.getCollisionShape(
                        EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        return (voxel.min(Direction.Axis.Y) == 0 && voxel.max(Direction.Axis.Y) == 0.5)
                || (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1);
    }

    public int getShapeType(BlockState state) {
        if (BlockModelCollisions.find(state).isDefined()) return 0;
        if (state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return 1;
        if (isXZFullShape(state)) {
            var voxel =
                    state.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            if (voxel.min(Direction.Axis.Y) == 0 && voxel.max(Direction.Axis.Y) == 0.5) return 3;
            if (voxel.min(Direction.Axis.Y) == 0.5 && voxel.max(Direction.Axis.Y) == 1) return 2;
            return 0;
        }
        var shape = getCollisionShape(state);
        return shape == FULL_BLOCK
                ? 1
                : shape == UP_HALF_BLOCK ? 2 : shape == DOWN_HALF_BLOCK ? 3 : 0;
    }
}
