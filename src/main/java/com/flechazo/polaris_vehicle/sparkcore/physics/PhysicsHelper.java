// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

import net.minecraft.world.phys.Vec3;

public final class PhysicsHelper {
    public static Vector3f toBVector3f(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    public static Vector3f toBVector3f(org.joml.Vector3f value) {
        return new Vector3f(value.x, value.y, value.z);
    }

    public static Matrix3f toBMatrix3f(org.joml.Matrix3f m) {
        return new Matrix3f(
                m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    public static org.joml.Matrix3f toMatrix3f(Matrix3f m) {
        return new org.joml.Matrix3f(
                m.get(0, 0),
                m.get(0, 1),
                m.get(0, 2),
                m.get(1, 0),
                m.get(1, 1),
                m.get(1, 2),
                m.get(2, 0),
                m.get(2, 1),
                m.get(2, 2));
    }
}
