package com.flechazo.polaris_vehicle.vehicle.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;

import java.lang.ref.Reference;
import java.util.List;

public final class VehiclePhysicsNativeStep {

    public static void run(PhysicsSpace world, float dt, List<PhysicsTickListener> listeners, Runnable solve) {
        for (var listener : listeners) listener.prePhysicsTick(world, dt);
        // PRE may remove wheels, replace terrain, or fail partway through a mutation.
        // Checking before PRE misses those failures; throwing inside a JNI callback
        // merely leaves a pending exception while Bullet continues updateAabbs.
        VehiclePhysicsMembership.repairBeforeStep(world);
        var bodies = world.getPcoList();
        var joints = world.getJointList();
        try {
            solve.run();
        } finally {
            // Keep wrappers (and their owned shapes/motion states) alive until JNI returns.
            Reference.reachabilityFence(bodies);
            Reference.reachabilityFence(joints);
        }
        for (var listener : listeners) listener.physicsTick(world, dt);
    }
}
