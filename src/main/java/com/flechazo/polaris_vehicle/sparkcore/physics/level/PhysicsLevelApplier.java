// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.level;

import com.flechazo.polaris_vehicle.sparkcore.api.SparkLevel;
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsLevelInitEvent;
import com.flechazo.polaris_vehicle.sparkcore.mixin_interface.ILevelMixin;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class PhysicsLevelApplier {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void load(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (SparkLevel.getPhysicsLevelIfPresent(level) != null) return;
            var physics = new ServerPhysicsLevel(level, 5, false);
            ((ILevelMixin) level).setPhysicsLevel(physics);
            physics.start(() -> NeoForge.EVENT_BUS.post(new PhysicsLevelInitEvent(physics)));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var physics = SparkLevel.getPhysicsLevelIfPresent(level);
            if (physics != null) physics.close();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pre(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var physics = SparkLevel.getPhysicsLevelIfPresent(level);
            if (physics == null || physics.getTerrainManager() == null) return;
            SparkLevel.processTasks(level, PPhase.ALL);
            SparkLevel.processTasks(level, PPhase.PRE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void post(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var physics = SparkLevel.getPhysicsLevelIfPresent(level);
            if (physics == null || physics.getTerrainManager() == null) return;
            SparkLevel.processTasks(level, PPhase.ALL);
            SparkLevel.processTasks(level, PPhase.POST);
            physics.requestStep();
        }
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            var physics = SparkLevel.getPhysicsLevelIfPresent(level);
            if (physics != null && physics.getTerrainManager() != null)
                physics.getTerrainManager().onChunkLoaded(chunk.getPos(), chunk);
        }
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var physics = SparkLevel.getPhysicsLevelIfPresent(level);
            if (physics != null && physics.getTerrainManager() != null)
                physics.getTerrainManager().onChunkUnloaded(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void ticketUpdate(ChunkTicketLevelUpdatedEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var physics = SparkLevel.getPhysicsLevelIfPresent(level);
        if (physics == null || physics.getTerrainManager() == null) return;
        var pos = new ChunkPos(event.getChunkPos());
        if (event.getNewTicketLevel() > 33
                && physics.getTickCount() > 0
                && physics.getTerrainManager().loaded(pos))
            physics.getTerrainManager().unloadPhysicsChunk(pos);
    }
}
