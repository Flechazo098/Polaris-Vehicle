package com.flechazo.polaris_vehicle.api.collision;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.Try;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.data.Chain;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureDocument;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockBonePose;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Register once from a mod constructor, before block states are baked. Only explicitly resolved
 * blocks opt in. Models are authoritative bundled resources, not client-only resource pack overrides.
 * No JNI allocations occur during registration or ordinary vanilla shape queries.
 */
public final class BlockModelCollisions {
    private BlockModelCollisions() {}
    public record Definition(ResourceLocation geometry, Function<String, BedrockBonePose> pose) {
        public static Definition of(ResourceLocation geometry) { return new Definition(geometry, name -> BedrockBonePose.IDENTITY); }
    }
    private record Registration(String modId, Class<?> owner, Function<BlockState, Maybe<Definition>> resolver) {}
    private record Cached(BlockState state, Maybe<BlockCollisionGeometry> geometry) {}
    private static volatile Chain<Registration> registrations = Chain.empty();
    private static volatile Chain<Cached> cache = Chain.empty();

    public static synchronized void registerMod(String modId, Class<?> owner, Function<BlockState, Maybe<Definition>> resolver) {
        ResourceLocation.fromNamespaceAndPath(modId, "validation");
        Maybe.some(modId).filter(id -> registrations.filter(registration -> registration.modId().equals(id)).head().isDefined())
                .ifPresent(id -> { throw new IllegalStateException("Block collision namespace already registered: " + id); });
        registrations = registrations.append(new Registration(modId, owner, resolver));
        cache = Chain.empty();
    }

    public static Maybe<BlockCollisionGeometry> find(BlockState state) {
        // Non-opted blocks remain on vanilla/Spark's existing path, without allocating cache entries.
        return registrations.filter(registration -> registration.modId().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()))
                .head().flatMap(registration -> cached(state, registration));
    }

    private static synchronized Maybe<BlockCollisionGeometry> cached(BlockState state, Registration registration) {
        return cache.filter(entry -> entry.state() == state).head().map(Cached::geometry).orElseGet(() -> {
            var geometry = registration.resolver().apply(state).map(definition -> load(registration, state, definition));
            cache = cache.append(new Cached(state, geometry));
            return geometry;
        });
    }

    private static BlockCollisionGeometry load(Registration registration, BlockState state, Definition definition) {
        var id = definition.geometry();
        String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
        return Try.of(() -> Pathway.ioAutoResource(() -> new InputStreamReader(
                        Maybe.ofNullable(registration.owner().getResourceAsStream(path))
                                .orElseGet(() -> { throw new IllegalArgumentException("Missing registered collision geometry " + path); }), StandardCharsets.UTF_8))
                .useSync(reader -> CodecUtil.readJson(BedrockStructureDocument.CODEC, reader)).unsafeRun())
                .map(document -> BlockCollisionGeometry.compile(document,
                        state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH,
                        definition.pose()))
                .getOrThrow(error -> new IllegalStateException("Cannot compile block collision: " + state + " from " + path, error));
    }
}
