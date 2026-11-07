// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.level;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public final class ServerPhysicsLevel extends PhysicsLevel {
    public ServerPhysicsLevel(ServerLevel level, int steps, boolean singleThreadMode) {
        super("Server PhysicsThread", level, steps, singleThreadMode);
    }

    @Override
    public ServerLevel getMcLevel() {
        return (ServerLevel) super.getMcLevel();
    }

    @Override
    public List<Entity> requestEntities() {
        var entities = new ArrayList<Entity>();
        getMcLevel().getAllEntities().forEach(entities::add);
        return entities;
    }
}
