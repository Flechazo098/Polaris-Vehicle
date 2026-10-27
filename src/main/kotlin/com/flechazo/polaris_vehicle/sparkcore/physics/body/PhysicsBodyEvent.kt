// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/body/PhysicsBodyEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.body

import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost
import com.flechazo.polaris_vehicle.sparkcore.util.CancellableEvent
import com.flechazo.polaris_vehicle.sparkcore.util.InlineEvent
import com.jme3.bullet.collision.PhysicsCollisionObject

abstract class PhysicsBodyEvent: InlineEvent {

    abstract class Collide(
        val o1: PhysicsCollisionObject,
        val o2: PhysicsCollisionObject,
        val o1Point: ManifoldPoint,
        val o2Point: ManifoldPoint,
    ): PhysicsBodyEvent() {
        class Pre(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
            override var canceled: Boolean = false,
        ): Collide(o1, o2, o1Point, o2Point), CancellableEvent

        class Started(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)

        class Processed(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)

        class Ended(
            o1: PhysicsCollisionObject,
            o2: PhysicsCollisionObject,
            o1Point: ManifoldPoint,
            o2Point: ManifoldPoint,
        ): Collide(o1, o2, o1Point, o2Point)
    }

    class AddToWorld: PhysicsBodyEvent()

    object Tick: PhysicsBodyEvent()

    abstract class PhysicsTick: PhysicsBodyEvent() {
        class Pre: PhysicsTick()
        class Post: PhysicsTick()
    }

    class OwnerChanged(
        val originOwner: PhysicsHost?,
        val originNewOwner: PhysicsHost?,
    ): PhysicsBodyEvent() {
        var newOwner: PhysicsHost? = originNewOwner
    }

}