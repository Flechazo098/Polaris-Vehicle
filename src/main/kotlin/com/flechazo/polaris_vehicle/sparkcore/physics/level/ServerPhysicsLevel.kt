// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/level/ServerPhysicsLevel.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.level

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel,
    baseStep : Int,
    singleThreadMode: Boolean = false,
) : PhysicsLevel("Server PhysicsThread", mcLevel, baseStep, singleThreadMode) {

    override fun requestEntities(): List<Entity> {
        return mcLevel.allEntities.toList()
    }

}