// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.event;

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;

import net.neoforged.neoforge.event.level.LevelEvent;

public abstract class PhysicsLevelTickEvent extends LevelEvent {
    private final PhysicsLevel physicsLevel;

    protected PhysicsLevelTickEvent(PhysicsLevel level) {
        super(level.getMcLevel());
        physicsLevel = level;
    }

    public PhysicsLevel getPhysicsLevel() {
        return physicsLevel;
    }

    public static final class Pre extends PhysicsLevelTickEvent {
        public Pre(PhysicsLevel level) {
            super(level);
        }
    }

    public static final class Post extends PhysicsLevelTickEvent {
        public Post(PhysicsLevel level) {
            super(level);
        }
    }
}
