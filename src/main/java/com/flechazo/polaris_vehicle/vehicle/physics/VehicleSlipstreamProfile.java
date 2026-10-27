package com.flechazo.polaris_vehicle.vehicle.physics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Comparator;
import java.util.List;

public record VehicleSlipstreamProfile(
        double maximumDistance,
        double halfWidth,
        double minimumDirectionDot,
        List<VehicleSlipstreamTier> tiers
) {

    public static final VehicleSlipstreamProfile DEFAULT = new VehicleSlipstreamProfile(
            10.0,
            2.0,
            0.5,
            List.of(
                    new VehicleSlipstreamTier(20.0, 1.0),
                    new VehicleSlipstreamTier(24.0, 1.5),
                    new VehicleSlipstreamTier(30.0, 2.0)
            )
    );

    public static final Codec<VehicleSlipstreamProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("maximum_distance")
                    .forGetter(VehicleSlipstreamProfile::maximumDistance),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("half_width")
                    .forGetter(VehicleSlipstreamProfile::halfWidth),
            Codec.doubleRange(-1.0, 1.0).fieldOf("minimum_direction_dot")
                    .forGetter(VehicleSlipstreamProfile::minimumDirectionDot),
            VehicleSlipstreamTier.CODEC.listOf().fieldOf("tiers")
                    .forGetter(VehicleSlipstreamProfile::tiers)
    ).apply(instance, VehicleSlipstreamProfile::new));

    public VehicleSlipstreamProfile {
        tiers = tiers.stream()
                .sorted(Comparator.comparingDouble(VehicleSlipstreamTier::minimumFrontSpeed))
                .toList();
    }

    public double bonusForSpeed(double frontSpeed) {
        double bonus = 0.0;
        for (VehicleSlipstreamTier tier : tiers) {
            if (frontSpeed > tier.minimumFrontSpeed()) {
                bonus = tier.maxSpeedBonus();
            }
        }
        return bonus;
    }
}
