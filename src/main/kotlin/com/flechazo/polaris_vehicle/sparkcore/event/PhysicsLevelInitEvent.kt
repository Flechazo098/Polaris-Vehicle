// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: event/PhysicsLevelInitEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.event

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel
import net.neoforged.neoforge.event.level.LevelEvent
/**
 * 在物理世界完全完成初始化后触发
 */
class PhysicsLevelInitEvent(val level: PhysicsLevel): LevelEvent(level.mcLevel) {}