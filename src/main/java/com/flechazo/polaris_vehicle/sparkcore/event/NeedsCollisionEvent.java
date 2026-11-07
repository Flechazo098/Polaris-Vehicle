// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.event;

import com.jme3.bullet.collision.PhysicsCollisionObject;

import net.neoforged.bus.api.Event;

public final class NeedsCollisionEvent extends Event {
    private final PhysicsCollisionObject pcoA, pcoB;
    private final boolean originShouldCollide;
    private boolean shouldCollide;

    public NeedsCollisionEvent(
            PhysicsCollisionObject a, PhysicsCollisionObject b, boolean collide) {
        pcoA = a;
        pcoB = b;
        originShouldCollide = collide;
        shouldCollide = collide;
    }

    public PhysicsCollisionObject getPcoA() {
        return pcoA;
    }

    public PhysicsCollisionObject getPcoB() {
        return pcoB;
    }

    public boolean getOriginShouldCollide() {
        return originShouldCollide;
    }

    public boolean getShouldCollide() {
        return shouldCollide;
    }

    public void setShouldCollide(boolean value) {
        shouldCollide = value;
    }
}
