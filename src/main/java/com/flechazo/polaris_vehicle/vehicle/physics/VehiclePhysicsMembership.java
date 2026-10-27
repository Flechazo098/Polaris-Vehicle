package com.flechazo.polaris_vehicle.vehicle.physics;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;

import java.util.ArrayList;

public final class VehiclePhysicsMembership {

    public static void repairBeforeStep(PhysicsSpace world) {
        var bodies = world.getPcoList();
        ArrayList<PhysicsCollisionObject> invalid = null;
        for (var body : bodies) {
            if (!body.isInWorld() || body.getCollisionSpace() != world) {
                if (invalid == null) invalid = new ArrayList<>();
                invalid.add(body);
            }
        }
        if (invalid != null) for (var body : invalid) {
            var nativeWorld = body.getCollisionSpace();
            if (nativeWorld != null && nativeWorld != world) {
                throw new IllegalStateException("Body " + body + " listed in " + world + " but owned by " + nativeWorld);
            }
            PolarisVehicle.LOGGER.error(
                    "[vehicle-physics-membership] repairing missing broadphase proxy body={} name={} user={} world={} thread={}",
                    body, body.name, body.getUserObject(), world, Thread.currentThread().getName());
            world.removeCollisionObject(body);
            world.addCollisionObject(body);
            if (!body.isInWorld() || body.getCollisionSpace() != world) {
                throw new IllegalStateException("Cannot restore broadphase membership of " + body);
            }
        }
        int nativeCount = world.countCollisionObjects();
        if (nativeCount != bodies.size()) {
            throw new IllegalStateException("[vehicle-physics-membership] Native/Java collision array mismatch in "
                    + world + ": native=" + nativeCount + " java=" + bodies.size());
        }
        for (var joint : world.getJointList()) {
            var a = joint.getBodyA();
            var b = joint.getBodyB();
            if (joint.getPhysicsSpace() != world
                    || (a != null && (!world.contains(a) || !a.isInWorld() || a.getCollisionSpace() != world))
                    || (b != null && (!world.contains(b) || !b.isInWorld() || b.getCollisionSpace() != world))) {
                throw new IllegalStateException("[vehicle-physics-membership] Orphan joint " + joint
                        + " in " + world + ": bodyA=" + a + " bodyB=" + b);
            }
        }
    }
}
