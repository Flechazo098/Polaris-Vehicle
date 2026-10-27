// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: event/PhysicsLevelTickEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.event

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel
import net.neoforged.neoforge.event.level.LevelEvent

abstract class PhysicsLevelTickEvent(val level: PhysicsLevel): LevelEvent(level.mcLevel) {

    /**
     * 在物理步进之前调用，可在此时改变运动参数
     */
    class Pre(level: PhysicsLevel): PhysicsLevelTickEvent(level)

    class Post(level: PhysicsLevel): PhysicsLevelTickEvent(level)

}