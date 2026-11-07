// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

@FunctionalInterface
public interface InlineEventConsumer<T extends InlineEvent> {
    void invoke(T event);
}
