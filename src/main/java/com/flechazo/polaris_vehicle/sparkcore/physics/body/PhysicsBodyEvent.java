// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.body;

import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost;
import com.flechazo.polaris_vehicle.sparkcore.util.CancellableEvent;
import com.flechazo.polaris_vehicle.sparkcore.util.InlineEvent;
import com.jme3.bullet.collision.PhysicsCollisionObject;

public abstract class PhysicsBodyEvent implements InlineEvent {
    public abstract static class Collide extends PhysicsBodyEvent {
        private final PhysicsCollisionObject o1, o2;
        private final ManifoldPoint o1Point, o2Point;

        protected Collide(
                PhysicsCollisionObject o1,
                PhysicsCollisionObject o2,
                ManifoldPoint p1,
                ManifoldPoint p2) {
            this.o1 = o1;
            this.o2 = o2;
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

        public static final class Pre extends Collide implements CancellableEvent {
            public Pre(
                    PhysicsCollisionObject a,
                    PhysicsCollisionObject b,
                    ManifoldPoint p1,
                    ManifoldPoint p2) {
                super(a, b, p1, p2);
            }

            private boolean canceled;

            public boolean getCanceled() {
                return canceled;
            }

            public void setCanceled(boolean value) {
                canceled = value;
            }
        }

        public static final class Started extends Collide {
            public Started(
                    PhysicsCollisionObject a,
                    PhysicsCollisionObject b,
                    ManifoldPoint p1,
                    ManifoldPoint p2) {
                super(a, b, p1, p2);
            }
        }

        public static final class Processed extends Collide {
            public Processed(
                    PhysicsCollisionObject a,
                    PhysicsCollisionObject b,
                    ManifoldPoint p1,
                    ManifoldPoint p2) {
                super(a, b, p1, p2);
            }
        }

        public static final class Ended extends Collide {
            public Ended(
                    PhysicsCollisionObject a,
                    PhysicsCollisionObject b,
                    ManifoldPoint p1,
                    ManifoldPoint p2) {
                super(a, b, p1, p2);
            }
        }
    }

    public static final class AddToWorld extends PhysicsBodyEvent {}

    public static final class Tick extends PhysicsBodyEvent {
        public static final Tick INSTANCE = new Tick();

        private Tick() {}
    }

    public abstract static class PhysicsTick extends PhysicsBodyEvent {
        public static final class Pre extends PhysicsTick {}

        public static final class Post extends PhysicsTick {}
    }

    public static final class OwnerChanged extends PhysicsBodyEvent {
        private final PhysicsHost originOwner, originNewOwner;
        private PhysicsHost newOwner;

        public OwnerChanged(PhysicsHost old, PhysicsHost owner) {
            originOwner = old;
            originNewOwner = owner;
            newOwner = owner;
        }

        public PhysicsHost getOriginOwner() {
            return originOwner;
        }

        public PhysicsHost getOriginNewOwner() {
            return originNewOwner;
        }

        public PhysicsHost getNewOwner() {
            return newOwner;
        }

        public void setNewOwner(PhysicsHost value) {
            newOwner = value;
        }
    }
}
