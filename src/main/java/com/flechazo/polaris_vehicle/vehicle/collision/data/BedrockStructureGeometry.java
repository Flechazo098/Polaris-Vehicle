package com.flechazo.polaris_vehicle.vehicle.collision.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record BedrockStructureGeometry(List<BedrockStructureBone> bones) {
    public static final Codec<BedrockStructureGeometry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BedrockStructureBone.CODEC.listOf().fieldOf("bones").forGetter(BedrockStructureGeometry::bones)
    ).apply(instance, BedrockStructureGeometry::new));
}
