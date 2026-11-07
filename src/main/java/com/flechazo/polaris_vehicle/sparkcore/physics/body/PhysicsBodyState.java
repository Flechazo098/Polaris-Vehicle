// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.body;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.math.Transform;

public final class PhysicsBodyState {
    private final PhysicsCollisionObject body;
    private Transform lastTransform;
    private Transform transform;
    private BoundingBox cachedBoundingBox;

    public PhysicsBodyState(PhysicsCollisionObject body) {
        this.body = body;
        lastTransform = body.getTransform(null);
        transform = body.getTransform(null);
        cachedBoundingBox = body.boundingBox(null);
    }

    public Transform getLastTransform() {
        return lastTransform;
    }

    public Transform getTransform() {
        return transform;
    }

    public BoundingBox getCachedBoundingBox() {
        return cachedBoundingBox;
    }

    public void update() {
        lastTransform = transform;
        transform = body.getTransform(null);
        cachedBoundingBox = body.boundingBoxWithoutRecalculate(null);
    }
}
