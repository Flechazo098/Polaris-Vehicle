// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/level/PhysicsWorld.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.level

import com.jme3.bullet.PhysicsTickListener
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsNativeStep
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.BulletVehicleController
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleTerrainSectionLookup
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleWheelTerrainSurface
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection

import com.flechazo.polaris_vehicle.sparkcore.SparkCore
import com.flechazo.polaris_vehicle.sparkcore.event.NeedsCollisionEvent
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsContactEvent
import com.flechazo.polaris_vehicle.sparkcore.physics.body.ManifoldPoint
import com.flechazo.polaris_vehicle.sparkcore.physics.body.PhysicsBodyEvent
import com.flechazo.polaris_vehicle.sparkcore.physics.body.owner
import com.flechazo.polaris_vehicle.sparkcore.util.triggerEvent
import com.jme3.bullet.CollisionConfiguration
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.SolverMode
import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.bullet.collision.PersistentManifolds
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Vector3f
import net.neoforged.neoforge.common.NeoForge

class PhysicsWorld(val level: PhysicsLevel) : PhysicsSpace(
    Vector3f(-Int.MAX_VALUE.toFloat(), -1_000f, -Int.MAX_VALUE.toFloat()),
    Vector3f(Int.MAX_VALUE.toFloat(), 15_000f, Int.MAX_VALUE.toFloat()),
    BroadphaseType.DBVT, NativeLibrary.countThreads(), CollisionConfiguration(8192, 1)
) {



    // Keep Java lifecycle callbacks outside stepSimulation. A pending JNI
    // exception cannot stop Bullet from dereferencing a proxy invalidated by PRE.
    private val stepListeners = mutableListOf<PhysicsTickListener>()

    override fun addTickListener(listener: PhysicsTickListener) {
        check(listener !in stepListeners)
        stepListeners.add(listener)
    }

    override fun removeTickListener(listener: PhysicsTickListener) {
        stepListeners.remove(listener)
    }

    init {
        setGravity(Vector3f(0f, -9.81f, 0f))
        addTickListener(level)
        // 需要强制更新AABB，否则地形变化时碰撞箱无法正确生效
//        isForceUpdateAllAabbs = false
        this.solverInfo.setMode(
            SolverMode.SIMD
                    or SolverMode.WarmStart
                    or SolverMode.CacheFriendly
                    or SolverMode.CacheDirection
                    or SolverMode.ArticulatedWarmStart
                    or SolverMode.Interleave
        )
    }

    /**
     * @return 在碰撞预检测时是否能够继续执行后续真实碰撞及检测
     */
    override fun needsCollision(pcoA: PhysicsCollisionObject, pcoB: PhysicsCollisionObject): Boolean {
        try {
            if (!VehicleWheelTerrainSurface.canCollide(pcoA, pcoB)) return false
        } catch (failure: Throwable) {
            level.deferFailure(failure)
            return false
        }
        if (pcoA.isStatic && pcoB.isStatic) return false
        var r = true
        if ((pcoA.owner != null && pcoB.owner != null && pcoA.owner == pcoB.owner && pcoA.collideWithOwnerGroups and pcoB.collideWithOwnerGroups == 0)) r =
            false
        return r
    }

    var pointCount = 0
    var pointProcessed = 0
    var pointConceived = 0

    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject,
        manifoldPointId: Long
    ) {
        try {
            if (!processVehicleContact(pcoA, pcoB, manifoldPointId, 0, 1)) {
                processVehicleContact(pcoB, pcoA, manifoldPointId, 1, 0)
            }
        } catch (failure: Throwable) {
            level.deferFailure(failure)
        }
        pointProcessed++
//        if (true) {
//            ManifoldPoints.setDistance1(manifoldPointId, 1000f)
//            return
//        }
        pcoA.isColliding = true
        pcoB.isColliding = true
    }

    override fun onContactConceived(
        manifoldPointId: Long,
        manifoldId: Long,
        pcoA: PhysicsCollisionObject,
        pcoB: PhysicsCollisionObject
    ): Boolean {
        try {
            if (shouldSuppressVehicleContact(pcoA, pcoB, manifoldPointId, 0, 1)
                || shouldSuppressVehicleContact(pcoB, pcoA, manifoldPointId, 1, 0)) return false
            // Set first-contact support and tire friction before the first solve.
            if (!processVehicleContact(pcoA, pcoB, manifoldPointId, 0, 1)) {
                processVehicleContact(pcoB, pcoA, manifoldPointId, 1, 0)
            }
        } catch (failure: Throwable) {
            level.deferFailure(failure)
        }
        pointConceived++
//        if (true) return false
        pcoA.isColliding = true
        pcoB.isColliding = true
        return true
    }

    override fun update(timeInterval: Float, maxSteps: Int, stepFlags: Int) {
        pointConceived = 0
        pointProcessed = 0
        pointCount = 0
        require(maxSteps == 0) { "Vehicle world requires one explicit fixed substep" }
        VehiclePhysicsNativeStep.run(
            this, timeInterval, stepListeners.toList()
        ) {
            super.update(timeInterval, maxSteps, stepFlags)
            level.rethrowDeferredFailure()
        }
//        val manifoldsAfterUpdate = listManifoldIds().iterator()
//        pointCount = 0
//        while (manifoldsAfterUpdate.hasNext()) {
//            pointCount += PersistentManifolds.countPoints(manifoldsAfterUpdate.next())
//        }
//        if (!level.mcLevel.isClientSide && pcoList.isNotEmpty() && (pointConceived > 0 || pointCount > 0 || pointProcessed > 0)) {
//            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Conceived: $pointConceived")
//            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Processed: $pointProcessed")
//            SparkCore.LOGGER.debug("tick: ${level.tickCount} Point Post     : $pointCount")
//            SparkCore.LOGGER.debug("-------------------------------------------------------")
//        }
    }

    override fun addCollisionObject(pco: PhysicsCollisionObject) {
        if (pco.isInWorld || contains(pco)) return
        super.addCollisionObject(pco)
        pco.triggerEvent(PhysicsBodyEvent.AddToWorld())
    }

    override fun removeCollisionObject(pco: PhysicsCollisionObject) {
        super.removeCollisionObject(pco)
    }

    private fun processVehicleContact(
        vehicle: PhysicsCollisionObject, terrainBody: PhysicsCollisionObject,
        pointId: Long, vehicleIndex: Int, terrainIndex: Int
    ): Boolean {
        val controller = vehicle.userObject as? BulletVehicleController ?: return false
        if (terrainBody.userObject is VehicleWheelTerrainSurface.Ownership) {
            return controller.processTerrainContact(vehicle, terrainBody, pointId, vehicleIndex, terrainIndex) { null }
        }
        val terrain = terrainBody.owner as? PhysicsChunkSection ?: return false
        return controller.processTerrainContact(vehicle, terrainBody, pointId, vehicleIndex, terrainIndex) { pos ->
            VehicleTerrainSectionLookup.stateAt(terrain, pos)
        }
    }

    private fun shouldSuppressVehicleContact(
        vehicle: PhysicsCollisionObject, terrainBody: PhysicsCollisionObject,
        pointId: Long, vehicleIndex: Int, terrainIndex: Int
    ): Boolean {
        val controller = vehicle.userObject as? BulletVehicleController ?: return false
        val terrain = terrainBody.owner as? PhysicsChunkSection ?: return false
        return controller.shouldSuppressTerrainContact(vehicle, terrainBody, pointId, vehicleIndex, terrainIndex) { pos ->
            VehicleTerrainSectionLookup.stateAt(terrain, pos)
        }
    }
}