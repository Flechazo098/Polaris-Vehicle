// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.body;

import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;

import org.jetbrains.annotations.NotNull;

public record ManifoldPoint(long id, int index) {

    public float getAppliedImpulse() {
        return ManifoldPoints.getAppliedImpulse(this.id);
    }

    public float getAppliedImpulseLateral1() {
        return ManifoldPoints.getAppliedImpulseLateral1(this.id);
    }

    public float getAppliedImpulseLateral2() {
        return ManifoldPoints.getAppliedImpulseLateral2(this.id);
    }

    public float getCombinedFriction() {
        return ManifoldPoints.getCombinedFriction(this.id);
    }

    public float getCombinedRestitution() {
        return ManifoldPoints.getCombinedRestitution(this.id);
    }

    public float getCombinedRollingFriction() {
        return ManifoldPoints.getCombinedRollingFriction(this.id);
    }

    public float getCombinedSpinningFriction() {
        return ManifoldPoints.getCombinedSpinningFriction(this.id);
    }

    public float getContactMotion1() {
        return ManifoldPoints.getContactMotion1(this.id);
    }

    public float getContactMotion2() {
        return ManifoldPoints.getContactMotion2(this.id);
    }

    public float getDistance() {
        return ManifoldPoints.getDistance1(this.id);
    }

    public int getFlags() {
        return ManifoldPoints.getFlags(this.id);
    }

    public int getTriangleIndex() {
        return this.index == 0
                ? ManifoldPoints.getIndex0(this.id)
                : ManifoldPoints.getIndex1(this.id);
    }

    public int getPartId() {
        return this.index == 0
                ? ManifoldPoints.getPartId0(this.id)
                : ManifoldPoints.getPartId1(this.id);
    }

    public int getLifeTime() {
        return ManifoldPoints.getLifeTime(this.id);
    }

    @NotNull
    public Vector3f getLateralFrictionDir1(@NotNull Vector3f storeVector) {

        ManifoldPoints.getLateralFrictionDir1(this.id, storeVector);
        return storeVector;
    }

    @NotNull
    public Vector3f getLateralFrictionDir2(@NotNull Vector3f storeVector) {

        ManifoldPoints.getLateralFrictionDir2(this.id, storeVector);
        return storeVector;
    }

    @NotNull
    public Vector3f getLocalPoint(@NotNull Vector3f storeVector) {

        if (this.index == 0) {
            ManifoldPoints.getLocalPointA(this.id, storeVector);
        } else {
            ManifoldPoints.getLocalPointB(this.id, storeVector);
        }

        return storeVector;
    }

    @NotNull
    public Vector3f getNormalWorld(@NotNull Vector3f storeVector) {

        ManifoldPoints.getNormalWorldOnB(this.id, storeVector);
        if (this.index == 0) {
            storeVector.multLocal(-1.0F);
        }

        return storeVector;
    }

    @NotNull
    public Vector3f getPositionWorld(@NotNull Vector3f storeVector) {

        if (this.index == 0) {
            ManifoldPoints.getPositionWorldOnA(this.id, storeVector);
        } else {
            ManifoldPoints.getPositionWorldOnB(this.id, storeVector);
        }

        return storeVector;
    }

    @NotNull
    public Vec3d getPositionWorldDp(@NotNull Vec3d storeVector) {

        if (this.index == 0) {
            ManifoldPoints.getPositionWorldOnADp(this.id, storeVector);
        } else {
            ManifoldPoints.getPositionWorldOnBDp(this.id, storeVector);
        }

        return storeVector;
    }

    public void setAppliedImpulse(float impulse) {
        ManifoldPoints.setAppliedImpulse(this.id, impulse);
    }

    public void setAppliedImpulseLateral1(float impulse) {
        ManifoldPoints.setAppliedImpulseLateral1(this.id, impulse);
    }

    public void setAppliedImpulseLateral2(float impulse) {
        ManifoldPoints.setAppliedImpulseLateral2(this.id, impulse);
    }

    public void setCombinedFriction(float friction) {
        ManifoldPoints.setCombinedFriction(this.id, friction);
    }

    public void setCombinedRestitution(float restitution) {
        ManifoldPoints.setCombinedRestitution(this.id, restitution);
    }

    public void setCombinedRollingFriction(float friction) {
        ManifoldPoints.setCombinedRollingFriction(this.id, friction);
    }

    public void setCombinedSpinningFriction(float friction) {
        ManifoldPoints.setCombinedSpinningFriction(this.id, friction);
    }

    public void setContactMotion1(float motion) {
        ManifoldPoints.setContactMotion1(this.id, motion);
    }

    public void setContactMotion2(float motion) {
        ManifoldPoints.setContactMotion2(this.id, motion);
    }

    public void setDistance(float distance) {
        ManifoldPoints.setDistance1(this.id, distance);
    }

    public void setFlags(int bitmask) {
        ManifoldPoints.setFlags(this.id, bitmask);
    }

    public void setLateralFrictionDir1(@NotNull Vector3f direction) {

        ManifoldPoints.setLateralFrictionDir1(this.id, direction);
    }

    public void setLateralFrictionDir2(@NotNull Vector3f direction) {

        ManifoldPoints.setLateralFrictionDir2(this.id, direction);
    }

    public void setLocalPoint(@NotNull Vector3f locationVector) {

        if (this.index == 0) {
            ManifoldPoints.setLocalPointA(this.id, locationVector);
        } else {
            ManifoldPoints.setLocalPointB(this.id, locationVector);
        }
    }

    public void setNormalWorldOnB(@NotNull Vector3f normalVector) {

        ManifoldPoints.setNormalWorldOnB(this.id, normalVector);
    }

    public void setPositionWorld(@NotNull Vector3f locationVector) {

        if (this.index == 0) {
            ManifoldPoints.setPositionWorldOnA(this.id, locationVector);
        } else {
            ManifoldPoints.setPositionWorldOnB(this.id, locationVector);
        }
    }

    @NotNull
    public ManifoldPoint createTestPoint() {
        long id = ManifoldPoints.createTestPoint();
        return new ManifoldPoint(id, 0);
    }

    public boolean isContactCalcArea3Points() {
        return ManifoldPoints.isContactCalcArea3Points();
    }

    public void setContactCalcArea3Points(boolean setting) {
        ManifoldPoints.setContactCalcArea3Points(setting);
    }
}
