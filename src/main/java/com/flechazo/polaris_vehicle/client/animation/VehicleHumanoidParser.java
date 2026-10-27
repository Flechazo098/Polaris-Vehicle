package com.flechazo.polaris_vehicle.client.animation;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.*;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.convertPosition;
import static com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.convertRotation;

/**
 * Reads authored poses and bakes Bedrock box-UV driver geometry during resource reload.
 */
final class VehicleHumanoidParser {
    private static final Codec<Map<String, HumanoidAnimationPose>> ANIMATIONS =
            Codec.unboundedMap(Codec.STRING, HumanoidAnimationPose.CODEC).fieldOf("animations").codec();
    private static final Codec<Map<ResourceLocation, String>> RIDER_FRAMES =
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.STRING);
    private static final Codec<List<RawBone>> DRIVER_GEOMETRY = skeletonCodec(true);
    private static final Codec<List<RawBone>> CONTROL_GEOMETRY = skeletonCodec(false);

    static Map<ResourceLocation, String> loadRiderVehicleFrames(ResourceManager resources) {
        var resource = PolarisVehicle.id("rider_vehicle_frames.json");
        return Pathway.tryOf(() -> {
            try (Reader reader = resources.getResourceOrThrow(resource).openAsReader()) {
                return CodecUtil.readJson(RIDER_FRAMES, reader);
            }
        }).getOrElse(Map.of());
    }

    static Map<String, HumanoidAnimationPose> loadAnimations(ResourceManager resources, ResourceLocation resource) {
        return Pathway.tryOf(() -> {
            try (Reader reader = resources.getResourceOrThrow(resource).openAsReader()) {
                return CodecUtil.readJson(ANIMATIONS, reader);
            }
        }).getOrElse(Map.of());
    }

    static FirstPersonSkeleton loadFirstPersonSkeleton(ResourceManager resources, ResourceLocation resource) {
        return loadFirstPersonSkeleton(resources, resource, true);
    }

    static FirstPersonSkeleton loadFirstPersonSkeleton(ResourceManager resources, ResourceLocation resource,
                                                       boolean bakeGeometry) {
        return Pathway.tryOf(() -> {
            try (Reader reader = resources.getResourceOrThrow(resource).openAsReader()) {
                var geometry = JsonParser.parseReader(reader).getAsJsonObject()
                        .getAsJsonArray("minecraft:geometry").get(0);
                return bakeSkeleton((bakeGeometry ? DRIVER_GEOMETRY : CONTROL_GEOMETRY)
                        .parse(JsonOps.INSTANCE, geometry).getOrThrow());
            }
        }).getOrElse(FirstPersonSkeleton.EMPTY);
    }

    private static Codec<List<RawBone>> skeletonCodec(boolean bakeGeometry) {
        MapCodec<List<RawCube>> cubes = bakeGeometry
                ? RawCube.CODEC.listOf().optionalFieldOf("cubes", List.of())
                : MapCodec.unit(List.of());
        Codec<RawBone> bone = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(RawBone::name),
                CodecUtil.maybeFieldCodec("parent", Codec.STRING).forGetter(RawBone::parent),
                AnimationVector.CODEC.optionalFieldOf("pivot", AnimationVector.ZERO).forGetter(RawBone::pivot),
                AnimationVector.CODEC.optionalFieldOf("rotation", AnimationVector.ZERO).forGetter(RawBone::rotation),
                Codec.BOOL.optionalFieldOf("mirror", false).forGetter(RawBone::mirror),
                cubes.forGetter(RawBone::cubes)
        ).apply(instance, RawBone::new));
        return bone.listOf().fieldOf("bones").codec();
    }

    private static FirstPersonSkeleton bakeSkeleton(List<RawBone> source) {
        Map<String, RawBone> rawBones = new LinkedHashMap<>();
        source.forEach(bone -> rawBones.put(bone.name(), bone));
        Map<String, FirstPersonBone> bones = new LinkedHashMap<>();
        rawBones.forEach((name, raw) -> {
            AnimationVector absolute = convertPosition(raw.pivot());
            AnimationVector parentAbsolute = raw.parent()
                    .map(parent -> convertPosition(rawBones.get(parent).pivot()))
                    .orElse(AnimationVector.ZERO);
            var localPosition = new AnimationVector(absolute.x() - parentAbsolute.x(),
                    absolute.y() - parentAbsolute.y(), absolute.z() - parentAbsolute.z());
            bones.put(name, new FirstPersonBone(name, raw.parent(), localPosition,
                    convertRotation(raw.rotation()), bakeCubes(raw)));
        });
        return new FirstPersonSkeleton(bones);
    }

    private static FirstPersonGeometry bakeCubes(RawBone bone) {
        AnimationVector pivot = convertPosition(bone.pivot());
        List<FirstPersonCube> cubes = new ArrayList<>();
        for (RawCube raw : bone.cubes()) {
            // Bedrock box UVs use an X-mirrored origin relative to the owning bone pivot.
            cubes.add(new FirstPersonCube(
                    -(raw.origin().x() + raw.size().x()) - pivot.x(),
                    raw.origin().y() - pivot.y(), raw.origin().z() - pivot.z(),
                    raw.size().x(), raw.size().y(), raw.size().z(),
                    raw.uv().getFirst(), raw.uv().get(1), raw.mirror().orElse(bone.mirror()), raw.inflate()));
        }
        return new FirstPersonGeometry(cubes);
    }

    private record RawBone(String name, Maybe<String> parent, AnimationVector pivot, AnimationVector rotation,
                           boolean mirror, List<RawCube> cubes) {
    }

    private record RawCube(AnimationVector origin, AnimationVector size, List<Integer> uv,
                           Maybe<Boolean> mirror, float inflate) {
        private static final Codec<RawCube> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                AnimationVector.CODEC.fieldOf("origin").forGetter(RawCube::origin),
                AnimationVector.CODEC.fieldOf("size").forGetter(RawCube::size),
                Codec.INT.listOf(2, 2).fieldOf("uv").forGetter(RawCube::uv),
                CodecUtil.maybeFieldCodec("mirror", Codec.BOOL).forGetter(RawCube::mirror),
                Codec.FLOAT.optionalFieldOf("inflate", 0F).forGetter(RawCube::inflate)
        ).apply(instance, RawCube::new));
    }
}
