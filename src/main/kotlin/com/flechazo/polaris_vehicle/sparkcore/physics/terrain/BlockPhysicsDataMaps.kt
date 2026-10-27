// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/terrain/BlockPhysicsDataMaps.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain

import net.minecraft.server.packs.resources.ResourceManager

import com.flechazo.polaris_vehicle.sparkcore.SparkCore
import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Block
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.registries.datamaps.DataMapType
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent

object BlockPhysicsDataMaps {

    @JvmField
    val BLOCK_PHYSICS_DATA: DataMapType<Block, BlockPhysicsData> = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "block_physics"),
        Registries.BLOCK,
        BlockPhysicsData.CODEC
    ).build()

    @SubscribeEvent
    fun register(event: RegisterDataMapTypesEvent) {
        event.register(BLOCK_PHYSICS_DATA)
    }

    // ---- 数据包热重载时清理缓存 ----

    /** 服务端重载 /reload 时清理 */
    @SubscribeEvent
    fun onAddReloadListener(event: AddReloadListenerEvent) {
        event.addListener(object : SimplePreparableReloadListener<Unit>() {
            override fun prepare(manager: ResourceManager, profiler: ProfilerFiller) {
                BlockCollisionUtil.invalidatePhysicsCache()
            }

            override fun apply(unit: Unit, manager: ResourceManager, profiler: ProfilerFiller) {
            }
        })
    }
}