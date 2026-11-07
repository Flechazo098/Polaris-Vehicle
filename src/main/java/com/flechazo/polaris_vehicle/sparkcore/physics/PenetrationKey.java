// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics;

import com.jme3.bullet.collision.PhysicsCollisionObject;

public record PenetrationKey(PhysicsHost owner, String zoneId) {
    public PhysicsHost getOwner() {
        return owner;
    }

    public String getZoneId() {
        return zoneId;
    }

    public static PenetrationKey fromCollision(PhysicsCollisionObject body, int triangleIndex) {
        return body.getUserObject() instanceof PhysicsHost host
                ? host.getPenetrationKey(body, triangleIndex)
                : null;
    }
}
