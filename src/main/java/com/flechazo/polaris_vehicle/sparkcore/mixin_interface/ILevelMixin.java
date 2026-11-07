// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: mixin_interface/ILevelMixin.java
package com.flechazo.polaris_vehicle.sparkcore.mixin_interface;

import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import org.jetbrains.annotations.Nullable;

public interface ILevelMixin {
    @Nullable PhysicsLevel getPhysicsLevelIfPresent();

    void setPhysicsLevel(PhysicsLevel physicsLevel);
}
