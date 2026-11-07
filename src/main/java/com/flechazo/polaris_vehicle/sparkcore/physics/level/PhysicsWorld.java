// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.level;

import com.flechazo.polaris_vehicle.sparkcore.physics.body.PhysicsBodyEvent;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.PhysicsBodyExtension;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsNativeStep;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.BulletVehicleController;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleTerrainSectionLookup;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleWheelTerrainSurface;
import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.SolverMode;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld extends PhysicsSpace {
    private final PhysicsLevel level;
    private final List<PhysicsTickListener> stepListeners = new ArrayList<>();
    private int pointCount, pointProcessed, pointConceived;

    public PhysicsWorld(PhysicsLevel level) {
        super(
                new Vector3f(-(float) Integer.MAX_VALUE, -1000, -(float) Integer.MAX_VALUE),
                new Vector3f((float) Integer.MAX_VALUE, 15000, (float) Integer.MAX_VALUE),
                BroadphaseType.DBVT,
                NativeLibrary.countThreads(),
                new CollisionConfiguration(8192, 1));
        this.level = level;
        setGravity(new Vector3f(0, -9.81F, 0));
        addTickListener(level);
        getSolverInfo()
                .setMode(
                        SolverMode.SIMD
                                | SolverMode.WarmStart
                                | SolverMode.CacheFriendly
                                | SolverMode.CacheDirection
                                | SolverMode.ArticulatedWarmStart
                                | SolverMode.Interleave);
    }

    public PhysicsLevel getLevel() {
        return level;
    }

    @Override
    public void addTickListener(PhysicsTickListener listener) {
        if (stepListeners.contains(listener))
            throw new IllegalStateException("Duplicate tick listener");
        stepListeners.add(listener);
    }

    @Override
    public void removeTickListener(PhysicsTickListener listener) {
        stepListeners.remove(listener);
    }

    @Override
    public boolean needsCollision(PhysicsCollisionObject a, PhysicsCollisionObject b) {
        try {
            if (!VehicleWheelTerrainSurface.canCollide(a, b)) return false;
        } catch (Throwable failure) {
            level.deferFailure(failure);
            return false;
        }
        if (a.isStatic() && b.isStatic()) return false;
        var owner = PhysicsBodyExtension.getOwner(a);
        return owner == null
                || PhysicsBodyExtension.getOwner(b) == null
                || !owner.equals(PhysicsBodyExtension.getOwner(b))
                || (a.collideWithOwnerGroups & b.collideWithOwnerGroups) != 0;
    }

    @Override
    public void onContactProcessed(PhysicsCollisionObject a, PhysicsCollisionObject b, long point) {
        try {
            if (!processVehicleContact(a, b, point, 0, 1)) processVehicleContact(b, a, point, 1, 0);
        } catch (Throwable failure) {
            level.deferFailure(failure);
        }
        pointProcessed++;
        a.isColliding = true;
        b.isColliding = true;
    }

    @Override
    public boolean onContactConceived(
            long point, long manifold, PhysicsCollisionObject a, PhysicsCollisionObject b) {
        try {
            if (shouldSuppressVehicleContact(a, b, point, 0, 1)
                    || shouldSuppressVehicleContact(b, a, point, 1, 0)) return false;
            if (!processVehicleContact(a, b, point, 0, 1)) processVehicleContact(b, a, point, 1, 0);
        } catch (Throwable failure) {
            level.deferFailure(failure);
        }
        pointConceived++;
        a.isColliding = true;
        b.isColliding = true;
        return true;
    }

    @Override
    public void update(float dt, int maxSteps, int flags) {
        pointConceived = 0;
        pointProcessed = 0;
        pointCount = 0;
        if (maxSteps != 0)
            throw new IllegalArgumentException("Vehicle world requires one explicit fixed substep");
        VehiclePhysicsNativeStep.run(
                this,
                dt,
                List.copyOf(stepListeners),
                () -> {
                    super.update(dt, maxSteps, flags);
                    level.rethrowDeferredFailure();
                });
    }

    @Override
    public void addCollisionObject(PhysicsCollisionObject body) {
        if (body.isInWorld() || contains(body)) return;
        super.addCollisionObject(body);
        body.triggerEvent(new PhysicsBodyEvent.AddToWorld());
    }

    private boolean processVehicleContact(
            PhysicsCollisionObject vehicle,
            PhysicsCollisionObject terrain,
            long point,
            int vehicleIndex,
            int terrainIndex) {
        if (!(vehicle.getUserObject() instanceof BulletVehicleController controller)) return false;
        if (terrain.getUserObject() instanceof VehicleWheelTerrainSurface.Ownership)
            return controller.processTerrainContact(
                    vehicle, terrain, point, vehicleIndex, terrainIndex, pos -> null);
        if (!(PhysicsBodyExtension.getOwner(terrain) instanceof PhysicsChunkSection section))
            return false;
        return controller.processTerrainContact(
                vehicle,
                terrain,
                point,
                vehicleIndex,
                terrainIndex,
                pos -> VehicleTerrainSectionLookup.stateAt(section, pos));
    }

    private boolean shouldSuppressVehicleContact(
            PhysicsCollisionObject vehicle,
            PhysicsCollisionObject terrain,
            long point,
            int vehicleIndex,
            int terrainIndex) {
        if (!(vehicle.getUserObject() instanceof BulletVehicleController controller)
                || !(PhysicsBodyExtension.getOwner(terrain) instanceof PhysicsChunkSection section))
            return false;
        return controller.shouldSuppressTerrainContact(
                vehicle,
                terrain,
                point,
                vehicleIndex,
                terrainIndex,
                pos -> VehicleTerrainSectionLookup.stateAt(section, pos));
    }

    public int getPointCount() {
        return pointCount;
    }

    public int getPointProcessed() {
        return pointProcessed;
    }

    public int getPointConceived() {
        return pointConceived;
    }
}
