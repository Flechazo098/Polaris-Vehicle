package com.flechazo.polaris_vehicle.vehicle.collision.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public record BedrockStructureBone(
        String name,
        Maybe<String> parent,
        Vec3 pivot,
        Vec3 rotation,
        Maybe<Double> inflate,
        Map<String, Vec3> locators,
        List<BedrockStructureCube> cubes
) {
    public static final Codec<BedrockStructureBone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BedrockStructureBone::name),
            CodecUtil.maybeFieldCodec("parent", Codec.STRING).forGetter(BedrockStructureBone::parent),
            Vec3.CODEC.optionalFieldOf("pivot", Vec3.ZERO).forGetter(BedrockStructureBone::pivot),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(BedrockStructureBone::rotation),
            CodecUtil.maybeFieldCodec("inflate", Codec.DOUBLE).forGetter(BedrockStructureBone::inflate),
            Codec.unboundedMap(Codec.STRING, Vec3.CODEC)
                    .optionalFieldOf("locators", Map.of())
                    .forGetter(BedrockStructureBone::locators),
            BedrockStructureCube.CODEC.listOf()
                    .optionalFieldOf("cubes", List.of())
                    .forGetter(BedrockStructureBone::cubes)
    ).apply(instance, BedrockStructureBone::new));
}
