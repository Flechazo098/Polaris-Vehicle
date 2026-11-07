// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.event;

public final class PhysicsEntityTickEvent extends net.neoforged.neoforge.event.entity.EntityEvent {
    public PhysicsEntityTickEvent(net.minecraft.world.entity.Entity entity) {
        super(entity);
    }
}
