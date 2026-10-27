package com.flechazo.polaris_vehicle.vehicle.collision.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record BedrockStructureDocument(List<BedrockStructureGeometry> geometries) {
    public static final Codec<BedrockStructureDocument> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BedrockStructureGeometry.CODEC.listOf().fieldOf("minecraft:geometry")
                    .forGetter(BedrockStructureDocument::geometries)
    ).apply(instance, BedrockStructureDocument::new));

    public BedrockStructureGeometry primaryGeometry() {
        return geometries.getFirst();
    }
}
