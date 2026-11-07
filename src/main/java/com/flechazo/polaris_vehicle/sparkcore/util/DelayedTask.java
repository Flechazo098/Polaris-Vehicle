// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

public record DelayedTask(int executeAtTick, Runnable task) {
    public int getExecuteAtTick() {
        return executeAtTick;
    }

    public Runnable getTask() {
        return task;
    }
}
