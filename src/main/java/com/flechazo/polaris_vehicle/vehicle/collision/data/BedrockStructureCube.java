package com.flechazo.polaris_vehicle.vehicle.collision.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

public record BedrockStructureCube(
        Vec3 origin,
        Vec3 size,
        Vec3 pivot,
        Vec3 rotation,
        Maybe<Double> inflate,
        boolean mirror
) {
    public static final Codec<BedrockStructureCube> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("origin").forGetter(BedrockStructureCube::origin),
            Vec3.CODEC.fieldOf("size").forGetter(BedrockStructureCube::size),
            Vec3.CODEC.optionalFieldOf("pivot", Vec3.ZERO).forGetter(BedrockStructureCube::pivot),
            Vec3.CODEC.optionalFieldOf("rotation", Vec3.ZERO).forGetter(BedrockStructureCube::rotation),
            CodecUtil.maybeFieldCodec("inflate", Codec.DOUBLE).forGetter(BedrockStructureCube::inflate),
            Codec.BOOL.optionalFieldOf("mirror", false).forGetter(BedrockStructureCube::mirror)
    ).apply(instance, BedrockStructureCube::new));

}
