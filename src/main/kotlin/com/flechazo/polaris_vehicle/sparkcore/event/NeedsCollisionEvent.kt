// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: event/NeedsCollisionEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.event

import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

class NeedsCollisionEvent(
    val pcoA: PhysicsCollisionObject,
    val pcoB: PhysicsCollisionObject,
    val originShouldCollide: Boolean
): Event() {

    var shouldCollide = originShouldCollide

}