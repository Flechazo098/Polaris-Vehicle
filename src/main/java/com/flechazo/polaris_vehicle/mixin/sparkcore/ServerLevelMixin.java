// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: mixin/terrain/ServerLevelMixin.java
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.mixin.sparkcore;

import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsManager;

import com.flechazo.polaris_vehicle.sparkcore.api.SparkLevel;
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHelperKt;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkManager;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.flechazo.polaris_vehicle.sparkcore.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void onBlockStateChange(BlockPos pos, BlockState blockState, BlockState newState, CallbackInfo ci) {
        if (SparkLevel.isSparkLevel((ServerLevel)(Object)this))
            VehiclePhysicsManager.terrainChanged((ServerLevel)(Object)this, pos);
        if (blockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && newState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return; // 更新前后均为完整方块，无需更新区块方体形状
        if (!SparkLevel.isSparkLevel((ServerLevel)(Object)this)) return;
        PhysicsLevel level = SparkLevel.getPhysicsLevel(((ServerLevel) (Object) this));
        PhysicsChunkManager terrainManager = level.getTerrainManager();
        if (terrainManager != null) {
            terrainManager.onBlockUpdated(Set.of(pos));
        }
    }
}
