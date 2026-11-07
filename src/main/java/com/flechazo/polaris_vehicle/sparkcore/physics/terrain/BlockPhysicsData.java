// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BlockPhysicsData(
        float friction,
        float rollingFriction,
        float restitution,
        float baseSlip,
        float slipFactor) {
    public static final Codec<BlockPhysicsData> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.FLOAT
                                                    .optionalFieldOf("friction", 0.7F)
                                                    .forGetter(BlockPhysicsData::friction),
                                            Codec.FLOAT
                                                    .optionalFieldOf("rolling_friction", 1F)
                                                    .forGetter(BlockPhysicsData::rollingFriction),
                                            Codec.FLOAT
                                                    .optionalFieldOf("restitution", 0.3F)
                                                    .forGetter(BlockPhysicsData::restitution),
                                            Codec.FLOAT
                                                    .optionalFieldOf("base_slip", 0F)
                                                    .forGetter(BlockPhysicsData::baseSlip),
                                            Codec.FLOAT
                                                    .optionalFieldOf("slip_factor", 1F)
                                                    .forGetter(BlockPhysicsData::slipFactor))
                                    .apply(instance, BlockPhysicsData::new));

    public float getFriction() {
        return friction;
    }

    public float getRollingFriction() {
        return rollingFriction;
    }

    public float getRestitution() {
        return restitution;
    }

    public float getBaseSlip() {
        return baseSlip;
    }

    public float getSlipFactor() {
        return slipFactor;
    }
}
