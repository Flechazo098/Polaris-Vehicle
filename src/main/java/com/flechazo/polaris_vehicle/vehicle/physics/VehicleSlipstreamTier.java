package com.flechazo.polaris_vehicle.vehicle.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record VehicleSlipstreamTier(double minimumFrontSpeed, double maxSpeedBonus) {
    public static final Codec<VehicleSlipstreamTier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("minimum_front_speed")
                    .forGetter(VehicleSlipstreamTier::minimumFrontSpeed),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("max_speed_bonus")
                    .forGetter(VehicleSlipstreamTier::maxSpeedBonus)
    ).apply(instance, VehicleSlipstreamTier::new));
}
