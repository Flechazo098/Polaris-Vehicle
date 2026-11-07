// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

public interface CancellableEvent extends InlineEvent {
    boolean getCanceled();

    void setCanceled(boolean canceled);
}
