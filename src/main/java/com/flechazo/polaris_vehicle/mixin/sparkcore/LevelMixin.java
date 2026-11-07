// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: mixin/extension/LevelMixin.java
package com.flechazo.polaris_vehicle.mixin.sparkcore;

import com.flechazo.polaris_vehicle.sparkcore.LevelPatch;
import com.flechazo.polaris_vehicle.sparkcore.mixin_interface.ILevelMixin;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.*;
import com.flechazo.polaris_vehicle.sparkcore.util.DelayedTask;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.flechazo.polaris_vehicle.sparkcore.util.TaskSubmitOffice;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelPatch, ILevelMixin {

    private final Level level = (Level) (Object) this;
    private volatile PhysicsLevel physicsLevel;
    private final HashMap<String, PhysicsCollisionObject> collisionObjects = new HashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Runnable>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Runnable>> imtasks = new ConcurrentHashMap<>();
    /** 去重延迟任务映射 */
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>> delayedTasks = new ConcurrentHashMap<>();
    /** 非去重延迟任务队列 */
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>> delayedTaskQueue = new ConcurrentHashMap<>();

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Runnable>> getTaskMap() {
        return tasks;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Runnable>> getImmediateQueue() {
        return imtasks;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>> getDelayedTaskMap() {
        return delayedTasks;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>> getDelayedTaskQueue() {
        return delayedTaskQueue;
    }

    @Override
    public @Nullable PhysicsLevel getPhysicsLevelIfPresent() {
        return physicsLevel;
    }

    @Override
    @NotNull
    public PhysicsLevel getPhysicsLevel() {
        if (physicsLevel == null) {
            throw new IllegalStateException("PhysicsLevel is not initialized for level class: " + level.getClass().getName());
        }
        return physicsLevel;
    }

    @Override
    public int getTickCount() {
        return (int) level.getGameTime();
    }

    @Override
    public @NotNull Map<@NotNull String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return collisionObjects;
    }

    public void setPhysicsLevel(@NotNull PhysicsLevel physicsLevel) {
        if (this.physicsLevel != null) {
            throw new IllegalStateException("PhysicsLevel has already been initialized for level class: " + level.getClass().getName());
        }
        this.physicsLevel = physicsLevel;
    }

}
