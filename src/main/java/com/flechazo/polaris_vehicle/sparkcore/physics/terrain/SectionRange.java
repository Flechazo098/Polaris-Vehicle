// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

public record SectionRange(int first, int last) {
    public boolean contains(int section) {
        return section >= first && section <= last;
    }
}
