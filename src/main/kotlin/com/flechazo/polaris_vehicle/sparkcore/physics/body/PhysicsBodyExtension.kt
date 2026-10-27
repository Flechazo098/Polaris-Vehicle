// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/body/PhysicsBodyExtension.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.body

import com.flechazo.polaris_vehicle.sparkcore.api.physicsLevel
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost
import com.flechazo.polaris_vehicle.sparkcore.physics.toBVector3f
import com.flechazo.polaris_vehicle.sparkcore.util.*
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Quaternionf
import org.joml.Vector3f


private val states = mutableMapOf<PhysicsCollisionObject, PhysicsBodyState>()
fun stateOf(collisionObject: PhysicsCollisionObject): PhysicsBodyState {
    return states.getOrPut(collisionObject) { PhysicsBodyState(collisionObject) }
}

var PhysicsCollisionObject.owner: PhysicsHost?
    get() = userObject as? PhysicsHost
    set(value) {
        val event = triggerEvent(PhysicsBodyEvent.OwnerChanged(owner, value))
        val value = event.newOwner
        value?.let {
            it.allPhysicsBodies[name] = this
        } ?: owner?.allPhysicsBodies?.remove(name)
        userObject = value
    }

fun Level.addPhysicsBody(body: PhysicsCollisionObject) {
    physicsLevel.apply {
        submitImmediateTask {
            world.addCollisionObject(body)
        }
    }
}

fun Level.removePhysicsBody(body: PhysicsCollisionObject) {
    physicsLevel.apply {
        submitImmediateTask {
            body.owner = null
            if (body.isInWorld)
                world.removeCollisionObject(body)
        }
    }
}

private val subs = mutableMapOf<PhysicsCollisionObject, Subscription>()

fun PhysicsCollisionObject.onCollideStarted(handler: (PhysicsBodyEvent.Collide.Started) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Started> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollideProcessed(handler: (PhysicsBodyEvent.Collide.Processed) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Processed> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollideEnded(handler: (PhysicsBodyEvent.Collide.Ended) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Ended> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onCollidePre(handler: (PhysicsBodyEvent.Collide.Pre) -> Boolean): Subscription {
    return onEvent<PhysicsBodyEvent.Collide.Pre> { event ->
        // 调用处理器，得到返回值（true=继续，false=取消）
        val shouldCancel = !handler(event)   // 返回 true 表示允许碰撞，false 表示取消
        if (shouldCancel) {
            event.canceled = true
        }
    }
}

fun PhysicsCollisionObject.onTick(handler: (PhysicsBodyEvent.Tick) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.Tick> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onPrePhysicsTick(handler: (PhysicsBodyEvent.PhysicsTick.Pre) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.PhysicsTick.Pre> { event ->
        handler(event)
    }
}

fun PhysicsCollisionObject.onPostPhysicsTick(handler: (PhysicsBodyEvent.PhysicsTick.Post) -> Unit): Subscription {
    return onEvent<PhysicsBodyEvent.PhysicsTick.Post> { event ->
        handler(event)
    }
}

