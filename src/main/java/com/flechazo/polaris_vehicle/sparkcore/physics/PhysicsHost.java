// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics;

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;

import java.util.Map;

public interface PhysicsHost {
    PhysicsLevel getPhysicsLevel();

    Map<String, PhysicsCollisionObject> getAllPhysicsBodies();

    default PhysicsCollisionObject getPhysicsBody(String name) {
        return getAllPhysicsBodies().get(name);
    }

    default String getPenetrationZoneId(PhysicsCollisionObject body, int triangleIndex) {
        return null;
    }

    default PenetrationKey getPenetrationKey(PhysicsCollisionObject body, int triangleIndex) {
        return new PenetrationKey(this, getPenetrationZoneId(body, triangleIndex));
    }

    default boolean shouldCreateDefaultPhysicsBody() {
        return true;
    }
}
