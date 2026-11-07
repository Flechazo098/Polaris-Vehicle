// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHelper;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

public final class SparkMath {
    @NotNull
    public static Vec3 toRadians(@NotNull Vec3 value) {

        return new Vec3(Math.toRadians(value.x), Math.toRadians(value.y), Math.toRadians(value.z));
    }

    @NotNull
    public static Vector3f copy(@NotNull Vector3f value) {

        return new Vector3f(value);
    }

    public static float toRadians(float value) {
        return (float) Math.toRadians(value);
    }

    public static double toRadians(double value) {
        return Math.toRadians(value);
    }

    public static float toDegrees(float value) {
        return (float) Math.toDegrees(value);
    }

    @NotNull
    public static Vector3f getScaledAxisX(@NotNull Quaternionf value) {

        Matrix3f matrix = new Matrix3f().rotate(value);
        return new Vector3f(matrix.m00, matrix.m01, matrix.m02);
    }

    @NotNull
    public static Vector3f getScaledAxisY(@NotNull Quaternionf value) {

        Matrix3f matrix = new Matrix3f().rotate(value);
        return new Vector3f(matrix.m10, matrix.m11, matrix.m12);
    }

    @NotNull
    public static Vector3f getScaledAxisZ(@NotNull Quaternionf value) {

        Matrix3f matrix = new Matrix3f().rotate(value);
        return new Vector3f(matrix.m20, matrix.m21, matrix.m22);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull BlockPos value) {

        return new Vec3(value.getX(), value.getY(), value.getZ());
    }

    @NotNull
    public static Quaternionf copy(@NotNull Quaternionf value) {

        return new Quaternionf(value);
    }

    @NotNull
    public static Vec3 div(@NotNull Vec3 vector, double value) {

        return new Vec3(vector.x / value, vector.y / value, vector.z / value);
    }

    @NotNull
    public static Vec3 rotLerp(@NotNull Vec3 value, @NotNull Vec3 target, double progress) {

        Quaterniond start = new Quaterniond().rotateZYX(value.z, value.y, value.x);
        Quaterniond end = new Quaterniond().rotateZYX(target.z, target.y, target.x);
        Quaterniond result = start.slerp(end, progress);

        return toVec3(toEuler(result));
    }

    @NotNull
    public static Vec3 setX(@NotNull Vec3 value, double x) {

        return new Vec3(x, value.y, value.z);
    }

    @NotNull
    public static Vec3 setY(@NotNull Vec3 value, double y) {

        return new Vec3(value.x, y, value.z);
    }

    @NotNull
    public static Vec3 setZ(@NotNull Vec3 value, double z) {

        return new Vec3(value.x, value.y, z);
    }

    @NotNull
    public static Vector3d toEuler(@NotNull Quaterniond value) {

        Vector3d angles = new Vector3d();
        float sinr_cosp = 2 * (float) (value.w * value.x + value.y * value.z);
        float cosr_cosp = 1 - 2 * (float) (value.x * value.x + value.y * value.y);
        angles.x = FastMath.atan2(sinr_cosp, cosr_cosp);
        double sinp = 2 * (value.w * value.y - value.z * value.x);
        angles.y = Math.abs(sinp) >= 1.0 ? Math.copySign(Math.PI / 2, sinp) : Math.asin(sinp);
        float siny_cosp = 2 * (float) (value.w * value.z + value.x * value.y);
        float cosy_cosp = 1 - 2 * (float) (value.y * value.y + value.z * value.z);
        angles.z = FastMath.atan2(siny_cosp, cosy_cosp);
        return angles;
    }

    @NotNull
    public static Vector3f toVector3f(@NotNull BlockPos value) {

        return new Vector3f(value.getX(), value.getY(), value.getZ());
    }

    @NotNull
    public static Vector3f toEuler(@NotNull Quaternionf value) {

        return toVector3f(toEuler(new Quaterniond(value)));
    }

    @NotNull
    public static Vector3f rotLerp(
            @NotNull Vector3f value,
            @NotNull Vector3f target,
            double progress,
            @NotNull Vector3f dist) {

        Vector3f var10000 = dist.set(rotLerp(toVec3(value), toVec3(target), progress).toVector3f());

        return var10000;
    }

    public static Vec3 plus(@NotNull Vec3 value, @NotNull Vec3 v) {

        return value.add(v);
    }

    public static Vec3 minus(@NotNull Vec3 value, @NotNull Vec3 v) {

        return value.subtract(v);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull double[] value) {

        return new Vec3(value[0], value[1], value[2]);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull List<Double> value) {

        return new Vec3(
                value.get(0).doubleValue(), value.get(1).doubleValue(), value.get(2).doubleValue());
    }

    @NotNull
    public static Vector3f rotLerp(
            @NotNull Vector3f value, @NotNull Vector3f target, double progress) {

        return rotLerp(value, target, progress, value);
    }

    public static Quaternionf toQuaternionf(@NotNull Vector3f value) {

        return new Quaternionf().rotateXYZ(value.x, value.y, value.z);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull Vector3f value) {

        return new Vec3(value.x, value.y, value.z);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull Vector3d value) {

        return new Vec3(value.x, value.y, value.z);
    }

    @NotNull
    public static Vec3i toVec3i(@NotNull Vec3 value) {

        return new Vec3i((int) value.x, (int) value.y, (int) value.z);
    }

    @NotNull
    public static Vector3f toVector3f(@NotNull Vector3d value) {

        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    public static double toDegrees(double value) {
        return Math.toDegrees(value);
    }

    @NotNull
    public static Vector2d toVector2d(@NotNull Vec2 value) {

        return new Vector2d(value.x, value.y);
    }

    @NotNull
    public static Vec3 toDegrees(@NotNull Vec3 value) {

        return new Vec3(toDegrees(value.x), toDegrees(value.y), toDegrees(value.z));
    }

    @NotNull
    public static Vector3f toDegrees(@NotNull Vector3f value) {

        return new Vector3f(toDegrees(value.x), toDegrees(value.y), toDegrees(value.z));
    }

    @NotNull
    public static Vector3f toRadians(@NotNull Vector3f value) {

        return new Vector3f(toRadians(value.x), toRadians(value.y), toRadians(value.z));
    }

    @NotNull
    public static Vec3 wrapDegrees(@NotNull Vec3 value) {

        return new Vec3(
                Mth.wrapDegrees(value.x), Mth.wrapDegrees(value.y), Mth.wrapDegrees(value.z));
    }

    public static Vector3f wrapDegrees(@NotNull Vector3f value, @NotNull Vector3f dist) {

        return dist.set(
                Mth.wrapDegrees(value.x), Mth.wrapDegrees(value.y), Mth.wrapDegrees(value.z));
    }

    public static Vector3f wrapDegrees(@NotNull Vector3f value) {

        return wrapDegrees(value, value);
    }

    @NotNull
    public static Vector3f toVector3f(@NotNull com.jme3.math.Vector3f value) {

        return new Vector3f(value.x, value.y, value.z);
    }

    @NotNull
    public static Vec3 toVec3(@NotNull com.jme3.math.Vector3f value) {

        return toVec3(toVector3f(value));
    }

    @NotNull
    public static ChunkPos toChunkPos(@NotNull Vec3 value) {

        return new ChunkPos((int) value.x >> 4, (int) value.z >> 4);
    }

    @NotNull
    public static ChunkPos toChunkPos(@NotNull com.jme3.math.Vector3f value) {

        return new ChunkPos((int) value.x >> 4, (int) value.z >> 4);
    }

    @NotNull
    public static ChunkPos toChunkPos(@NotNull Vector3f value) {

        return new ChunkPos((int) value.x >> 4, (int) value.z >> 4);
    }

    @NotNull
    public static Matrix4f toMatrix4f(@NotNull com.jme3.math.Matrix4f value) {

        return new Matrix4f(
                value.m00, value.m10, value.m20, value.m30, value.m01, value.m11, value.m21,
                value.m31, value.m02, value.m12, value.m22, value.m32, value.m03, value.m13,
                value.m23, value.m33);
    }

    @NotNull
    public static Matrix3f copy(@NotNull Matrix3f value) {

        return new Matrix3f(
                value.m00, value.m10, value.m20, value.m01, value.m11, value.m21, value.m02,
                value.m12, value.m22);
    }

    @NotNull
    public static Quaternionf toQuaternionf(@NotNull Quaternion value) {

        return new Quaternionf(value.getX(), value.getY(), value.getZ(), value.getW());
    }

    @NotNull
    public static Quaternion toBQuaternion(@NotNull Quaternionf value) {

        return new Quaternion(value.x, value.y, value.z, value.w);
    }

    @NotNull
    public static Transform lerp(Transform value, Transform target, float delta) {
        return new Transform(
                PhysicsHelper.toBVector3f(
                        toVector3f(value.getTranslation())
                                .lerp(toVector3f(target.getTranslation()), delta)),
                toBQuaternion(
                        toQuaternionf(value.getRotation())
                                .slerp(toQuaternionf(target.getRotation()), delta)),
                PhysicsHelper.toBVector3f(
                        toVector3f(value.getScale()).lerp(toVector3f(target.getScale()), delta)));
    }

    @NotNull
    public static Matrix4f toMatrix4f(Transform value) {
        return new Matrix4f()
                .translationRotateScale(
                        toVector3f(value.getTranslation()),
                        toQuaternionf(value.getRotation()),
                        toVector3f(value.getScale()));
    }

    @NotNull
    public static Matrix4f lerp(@NotNull Matrix4f value, @NotNull Matrix4f target, float delta) {

        Vector3f t0 = new Vector3f();
        value.getTranslation(t0);
        Quaternionf r0 = new Quaternionf();
        value.getNormalizedRotation(r0);
        Vector3f s0 = new Vector3f();
        value.getScale(s0);
        Vector3f t1 = new Vector3f();
        target.getTranslation(t1);
        Quaternionf r1 = new Quaternionf();
        target.getNormalizedRotation(r1);
        Vector3f s1 = new Vector3f();
        target.getScale(s1);
        Matrix4f var10000 =
                new Matrix4f()
                        .translationRotateScale(
                                t0.lerp(t1, delta), r0.slerp(r1, delta), s0.lerp(s1, delta));

        return var10000;
    }

    @NotNull
    public static Vector3f catmullromVector3f(
            @NotNull Vector3f prev,
            @NotNull Vector3f now,
            @NotNull Vector3f next,
            @NotNull Vector3f nextNext,
            float t) {

        return new Vector3f(
                Mth.catmullrom(t, prev.x, now.x, next.x, nextNext.x),
                Mth.catmullrom(t, prev.y, now.y, next.y, nextNext.y),
                Mth.catmullrom(t, prev.z, now.z, next.z, nextNext.z));
    }

    @NotNull
    public static Vec3 catmullromVec3(Vec3 prev, Vec3 now, Vec3 next, Vec3 nextNext, float t) {
        return toVec3(
                catmullromVector3f(
                        prev.toVector3f(),
                        now.toVector3f(),
                        next.toVector3f(),
                        nextNext.toVector3f(),
                        t));
    }

    @NotNull
    public static AABB toAABB(BoundingBox value) {
        return new AABB(toVec3(value.getMin(null)), toVec3(value.getMax(null)));
    }

    @NotNull
    public static BoundingBox toBoundingBox(AABB value) {
        return new BoundingBox(
                PhysicsHelper.toBVector3f(value.getMinPosition()),
                PhysicsHelper.toBVector3f(value.getMaxPosition()));
    }
}
