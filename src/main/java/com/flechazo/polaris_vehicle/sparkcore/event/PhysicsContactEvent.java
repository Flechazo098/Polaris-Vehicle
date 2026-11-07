// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.event;

import com.flechazo.polaris_vehicle.sparkcore.physics.body.ManifoldPoint;
import com.jme3.bullet.collision.PhysicsCollisionObject;

import net.neoforged.bus.api.Event;

public abstract class PhysicsContactEvent extends Event {
    private final long manifoldId;

    protected PhysicsContactEvent(long id) {
        manifoldId = id;
    }

    public long getManifoldId() {
        return manifoldId;
    }

    public static final class Start extends PhysicsContactEvent {
        public Start(long id) {
            super(id);
        }
    }

    public static final class End extends PhysicsContactEvent {
        public End(long id) {
            super(id);
        }
    }

    public static final class Process extends PhysicsContactEvent {
        private final PhysicsCollisionObject o1, o2;
        private final ManifoldPoint o1Point, o2Point;

        public Process(
                PhysicsCollisionObject a,
                PhysicsCollisionObject b,
                ManifoldPoint p1,
                ManifoldPoint p2,
                long id) {
            super(id);
            o1 = a;
            o2 = b;
            o1Point = p1;
            o2Point = p2;
        }

        public PhysicsCollisionObject getO1() {
            return o1;
        }

        public PhysicsCollisionObject getO2() {
            return o2;
        }

        public ManifoldPoint getO1Point() {
            return o1Point;
        }

        public ManifoldPoint getO2Point() {
            return o2Point;
        }
    }
}
