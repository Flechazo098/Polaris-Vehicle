// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.body;

public final class CollisionGroups {
    public static final int NONE = 0;
    public static final int TERRAIN = 1;
    public static final int PHYSICS_BODY = 1 << 1;
    public static final int PAWN = 1 << 2;
    public static final int TRIGGER = 1 << 3;

    private CollisionGroups() {}
}
