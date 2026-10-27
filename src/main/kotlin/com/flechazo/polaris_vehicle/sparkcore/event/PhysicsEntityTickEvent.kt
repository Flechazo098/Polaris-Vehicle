// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: event/PhysicsEntityTickEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.event

import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.event.entity.EntityEvent

class PhysicsEntityTickEvent(entity: Entity): EntityEvent(entity) {
}