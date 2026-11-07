package com.flechazo.polaris_vehicle.vehicle.collision.data;

import net.minecraft.world.phys.Vec3;

/** Additive Bedrock animation pose: translation in pixels, rotation in degrees. */
public record BedrockBonePose(Vec3 translation, Vec3 rotation) {
    public static final BedrockBonePose IDENTITY = new BedrockBonePose(Vec3.ZERO, Vec3.ZERO);
}
