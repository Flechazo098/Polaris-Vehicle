package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import net.minecraft.world.phys.Vec3;

/**
 * Transform helpers shared by the chassis, suspension and contact solver.
 */
final class VehicleContactMath {
    private VehicleContactMath() {
    }

    static Vector3f vector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    /**
     * Subtract world positions before rounding to float-sized local offsets.
     */
    static Vector3f relativePosition(PhysicsRigidBody body, PhysicsRigidBody origin) {
        Vec3d position = body.getPhysicsLocationDp(null);
        Vec3d base = origin.getPhysicsLocationDp(null);
        return new Vector3f((float) (position.x - base.x), (float) (position.y - base.y), (float) (position.z - base.z));
    }

    static Vector3f rotate(Quaternion q, Vector3f v) {
        Vector3f u = new Vector3f(q.getX(), q.getY(), q.getZ());
        Vector3f uv = u.cross(v);
        return v.add(uv.mult(2 * q.getW())).addLocal(u.cross(uv).mult(2));
    }

    static Vector3f rotate(Quaternion q, Vec3 v) {
        return rotate(q, vector(v));
    }

}
