// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.event;

public final class PhysicsLevelInitEvent extends net.neoforged.neoforge.event.level.LevelEvent {
    private final com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel level;

    public PhysicsLevelInitEvent(
            com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel level) {
        super(level.getMcLevel());
        this.level = level;
    }

    public com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel getPhysicsLevel() {
        return level;
    }
}
