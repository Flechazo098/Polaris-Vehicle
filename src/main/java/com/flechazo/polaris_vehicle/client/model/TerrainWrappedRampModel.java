package com.flechazo.polaris_vehicle.client.model;

// import com.flechazo.polaris_vehicle.client.compat.NeoContinuityCompat;

import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.RampCollisionGeometry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * World-only ramp model that projects the actual baked top appearance of the supporting block onto the ramp.
 */
public final class TerrainWrappedRampModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Appearance> APPEARANCE = new ModelProperty<>();
    private static final int MAXIMUM_SOURCE_DEPTH = 64;
    private static final double HALF_RAMP_SURFACE_LENGTH = Math.sqrt(1.25D);
    private static final double FULL_RAMP_SURFACE_LENGTH = Math.sqrt(2.0D);
    private static final double CLIP_EPSILON = 1.0E-7D;
    private static final int AO_SLOPE_SEGMENTS = 4;
    private static final int AO_CROSS_SEGMENTS = 2;
    private static final boolean SLOPE_AMBIENT_OCCLUSION = false;
    private final boolean upper;
    private final boolean fullHeight;
    private final float lowHeight;
    private final float rise;
    private final Function<ModelResourceLocation, BakedModel> modelLookup;

    public TerrainWrappedRampModel(
            BakedModel originalModel,
            boolean upper,
            Function<ModelResourceLocation, BakedModel> modelLookup
    ) {
        this(originalModel, upper, false, modelLookup);
    }

    public TerrainWrappedRampModel(
            BakedModel originalModel,
            boolean upper,
            boolean fullHeight,
            Function<ModelResourceLocation, BakedModel> modelLookup
    ) {
        this(originalModel, upper ? 0.5F : 0F, fullHeight ? 1F : 0.5F, modelLookup);
        if (upper && fullHeight) throw new IllegalArgumentException("Ramp cannot be upper and full-height");
    }

    public TerrainWrappedRampModel(BakedModel originalModel, float lowHeight, float rise,
                                   Function<ModelResourceLocation, BakedModel> modelLookup) {
        super(originalModel);
        this.upper = lowHeight > 0;
        this.fullHeight = rise == 1;
        this.lowHeight = lowHeight;
        this.rise = rise;
        this.modelLookup = modelLookup;
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos position,
            BlockState state,
            ModelData modelData
    ) {
        Source source = findSource(level, position);
        if (source == null) {
            return modelData;
        }
        BakedModel sourceModel = modelLookup.apply(
                BlockModelShaper.stateToModelLocation(
                        source.state()
                )
        );
        if (sourceModel == null || sourceModel == this) {
            return modelData;
        }
        ModelData sourceData = level.getModelData(source.position());
        sourceData = sourceModel.getModelData(
                level,
                source.position(),
                source.state(),
                sourceData
        );
        Direction highEdge = state.getValue(RampBlock.FACING);
        BlockPos seamNeighbor = position.relative(highEdge.getOpposite());
        int joined = 0;
        for (Direction edge : new Direction[]{highEdge, highEdge.getOpposite()}) {
            BlockState neighbor = level.getBlockState(position.relative(edge));
            if (neighbor.getBlock() instanceof RampBlock other
                    && ModBlocks.isMimicRampId(BuiltInRegistries.BLOCK.getKey(other))
                    && neighbor.getValue(RampBlock.FACING) == highEdge
                    && Math.abs(other.rise() - rise) < 0.00001F
                    && Math.abs((edge == highEdge ? lowHeight + rise : lowHeight)
                    - (edge == highEdge ? other.lowHeight() : other.lowHeight() + other.rise())) < 0.00001F)
                joined |= edge == highEdge ? 1 : 2;
        }
        BlockPos variantPosition = source.position();
        if (upper && (joined & 2) != 0) {
            BlockPos previous = position;
            for (int step = 0; step < Math.round(lowHeight / rise); step++) {
                previous = previous.relative(highEdge.getOpposite());
                BlockState previousState = level.getBlockState(previous);
                if (!(previousState.getBlock() instanceof RampBlock other)
                        || !ModBlocks.isMimicRampId(BuiltInRegistries.BLOCK.getKey(other))
                        || previousState.getValue(RampBlock.FACING) != highEdge
                        || Math.abs(other.rise() - rise) > 0.00001F
                        || Math.abs(other.lowHeight() - (lowHeight - (step + 1) * rise)) > 0.00001F) break;
                Source lowerSource = findSource(level, previous);
                if (lowerSource == null || !lowerSource.state().equals(source.state())) break;
                variantPosition = lowerSource.position();
            }
        }
        Appearance appearance = bakeAppearance(
                level,
                state,
                position,
                variantPosition,
                joined,
                source,
                sourceModel,
                sourceData
        );
        return appearance == null
                ? modelData
                : modelData.derive().with(APPEARANCE, appearance).build();
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        Appearance appearance = modelData.get(APPEARANCE);
        if (appearance == null || state == null) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        // Every face is intentionally unculled. A sloped quad has no exact vanilla
        // cull direction, and classifying it as UP makes neighbor culling incorrect.
        if (side != null) {
            return List.of();
        }
        if (renderType != null) {
            return appearance.quads().getOrDefault(renderType, List.of());
        }
        List<BakedQuad> all = new ArrayList<>();
        appearance.quads().values().forEach(all::addAll);
        return List.copyOf(all);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState state,
            RandomSource random,
            ModelData modelData
    ) {
        Appearance appearance = modelData.get(APPEARANCE);
        return appearance == null
                ? originalModel.getRenderTypes(state, random, modelData)
                : ChunkRenderTypeSet.of(appearance.quads().keySet());
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData modelData) {
        Appearance appearance = modelData.get(APPEARANCE);
        return appearance == null
                ? originalModel.getParticleIcon(modelData)
                : appearance.particle();
    }

    private Appearance bakeAppearance(
            BlockAndTintGetter level,
            BlockState rampState,
            BlockPos rampPosition,
            BlockPos variantPosition,
            int joined,
            Source source,
            BakedModel sourceModel,
            ModelData sourceData
    ) {
        // Keep randomized top-model variants identical across a lower/upper joint.
        // The material itself still comes from the block directly below each ramp.
        long seed = source.state().getSeed(variantPosition);
        RandomSource typeRandom = RandomSource.create(seed);
        ChunkRenderTypeSet sourceTypes = sourceModel.getRenderTypes(
                source.state(),
                typeRandom,
                sourceData
        );
        Map<RenderType, List<BakedQuad>> result = new LinkedHashMap<>();
        TextureAtlasSprite particle = sourceModel.getParticleIcon(sourceData);
        Direction highEdge = rampState.getValue(RampBlock.FACING);
        SlopeAmbientOcclusion slopeAo = new SlopeAmbientOcclusion(
                level, rampPosition, highEdge, rise);

//         Map<RenderType, List<BakedQuad>> continuityQuads = new LinkedHashMap<>();
//         boolean continuityCaptured = false;
//         for (RenderType renderType : sourceTypes) {
//             List<BakedQuad> captured = NeoContinuityCompat.capture(sourceModel, level,
//                     source.state(), source.position(), sourceData, renderType, seed);
//             if (captured != null) {
//                 continuityCaptured = true;
//                 continuityQuads.put(renderType, captured);
//             }
//         }

        for (RenderType renderType : sourceTypes) {
//             List<BakedQuad> captured = continuityQuads.getOrDefault(renderType, List.of());
//             List<BakedQuad> topQuads = continuityCaptured
//                     ? filterFaceQuads(captured, Direction.UP)
//                     : sourceTopQuads(sourceModel, source, sourceData, renderType, seed);
            List<BakedQuad> topQuads = sourceTopQuads(
                    sourceModel, source, sourceData, renderType, seed);
            List<BakedQuad> rampQuads = new ArrayList<>(topQuads.size() + 5);
            for (BakedQuad topQuad : topQuads) {
                for (BakedQuad slope : projectTopQuad(topQuad, highEdge, rampPosition)) {
                    rampQuads.add(withSlopeAo(slope, slopeAo));
                }
            }
//             addBoundaryFaces(rampQuads, topQuads.isEmpty() ? null : topQuads.getFirst(),
//                     highEdge, joined, sourceModel, source, sourceData, renderType, seed,
//                     continuityCaptured ? captured : null);
            addBoundaryFaces(rampQuads, topQuads.isEmpty() ? null : topQuads.getFirst(),
                    highEdge, joined, sourceModel, source, sourceData, renderType, seed);
            if (!rampQuads.isEmpty()) {
                result.put(renderType, List.copyOf(rampQuads));
            }
        }

        if (result.isEmpty()) {
            RenderType fallbackType = sourceTypes.asList().isEmpty()
                    ? RenderType.solid()
                    : sourceTypes.asList().getFirst();
            result.put(fallbackType, fallbackGeometry(particle, highEdge, joined, slopeAo));
        }
        return new Appearance(Map.copyOf(result), particle);
    }

    private List<BakedQuad> sourceTopQuads(
            BakedModel model,
            Source source,
            ModelData data,
            RenderType renderType,
            long seed
    ) {
        Set<BakedQuad> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BakedQuad> result = new ArrayList<>();
        collectTopQuads(
                result,
                unique,
                model.getQuads(
                        source.state(),
                        Direction.UP,
                        RandomSource.create(seed),
                        data,
                        renderType
                )
        );
        collectTopQuads(
                result,
                unique,
                model.getQuads(
                        source.state(),
                        null,
                        RandomSource.create(seed),
                        data,
                        renderType
                )
        );
        return List.copyOf(result);
    }

    private static void collectTopQuads(
            List<BakedQuad> output,
            Set<BakedQuad> unique,
            List<BakedQuad> candidates
    ) {
        for (BakedQuad quad : candidates) {
            if (quad.getDirection() == Direction.UP && unique.add(quad)) {
                output.add(quad);
            }
        }
    }

    private List<BakedQuad> sourceFaceQuads(
            BakedModel model,
            Source source,
            ModelData data,
            RenderType renderType,
            long seed,
            Direction face
    ) {
        Set<BakedQuad> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BakedQuad> result = new ArrayList<>();
        collectFaceQuads(result, unique, model.getQuads(source.state(), face,
                RandomSource.create(seed), data, renderType), face);
        collectFaceQuads(result, unique, model.getQuads(source.state(), null,
                RandomSource.create(seed), data, renderType), face);
        return result;
    }

    private static void collectFaceQuads(List<BakedQuad> output, Set<BakedQuad> unique,
                                         List<BakedQuad> candidates, Direction face) {
        for (BakedQuad quad : candidates) {
            if (quad.getDirection() == face && unique.add(quad)) {
                output.add(quad);
            }
        }
    }

//         private static List<BakedQuad> filterFaceQuads(List<BakedQuad> candidates, Direction face) {
//         List<BakedQuad> result = new ArrayList<>();
//         for (BakedQuad quad : candidates) {
//             if (quad.getDirection() == face) {
//                 result.add(quad);
//             }
//         }
//         return List.copyOf(result);
//     }

    private List<BakedQuad> projectTopQuad(BakedQuad source, Direction highEdge, BlockPos position) {
        List<BakedQuad> wrapped = wrapSurfaceQuad(
                source, highEdge, position, lowHeight, rise);
        if (!wrapped.isEmpty()) {
            return subdivideForAo(wrapped, highEdge);
        }
        int[] vertices = Arrays.copyOf(source.getVertices(), source.getVertices().length);
        float[] normal = slopeNormal(highEdge, rise);
        int packedNormal = packNormal(normal[0], normal[1], normal[2]);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            float x = Float.intBitsToFloat(vertices[offset + IQuadTransformer.POSITION]);
            float z = Float.intBitsToFloat(vertices[offset + IQuadTransformer.POSITION + 2]);
            vertices[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(
                    surfaceY(highEdge, lowHeight, rise, x, z)
            );
            if (IQuadTransformer.NORMAL >= 0) {
                vertices[offset + IQuadTransformer.NORMAL] = packedNormal;
            }
        }
        return subdivideForAo(List.of(new BakedQuad(
                vertices,
                source.getTintIndex(),
                Direction.UP,
                source.getSprite(),
                source.isShade(),
                SLOPE_AMBIENT_OCCLUSION
        )), highEdge);
    }

    static List<BakedQuad> wrapSurfaceQuad(BakedQuad source, Direction highEdge,
                                           BlockPos position, boolean upper) {
        return wrapSurfaceQuad(source, highEdge, position, upper, false);
    }

    static List<BakedQuad> wrapSurfaceQuad(BakedQuad source, Direction highEdge,
                                           BlockPos position, boolean upper,
                                           boolean fullHeight) {
        return wrapSurfaceQuad(source, highEdge, position, upper ? 0.5F : 0F, fullHeight ? 1F : 0.5F);
    }

    static List<BakedQuad> wrapSurfaceQuad(BakedQuad source, Direction highEdge,
                                           BlockPos position, float lowHeight, float rise) {
        int[] original = source.getVertices();
        if (original.length != 4 * IQuadTransformer.STRIDE || IQuadTransformer.UV0 < 0) {
            return List.of();
        }
        List<int[]> polygon = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            polygon.add(Arrays.copyOfRange(original, i * IQuadTransformer.STRIDE,
                    (i + 1) * IQuadTransformer.STRIDE));
        }
        double[] gradient = uvGradient(polygon, highEdge, source.getSprite());
        if (gradient == null) {
            return List.of();
        }
        int blockAlong = position.getX() * highEdge.getStepX()
                + position.getZ() * highEdge.getStepZ();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int[] vertex : polygon) {
            double coordinate = surfaceCoordinate(
                    blockAlong, progress(highEdge, vertex), rise);
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }
        List<BakedQuad> result = new ArrayList<>();
        // A sprite occupies only one atlas rectangle: splitting at integer periods is
        // essential; a UV of 1.118 would sample an unrelated neighboring sprite.
        int firstPeriod = (int) Math.floor(min + CLIP_EPSILON);
        int lastPeriod = (int) Math.ceil(max - CLIP_EPSILON) - 1;
        for (int period = firstPeriod; period <= lastPeriod; period++) {
            double surfaceLength = Math.sqrt(1D + rise * rise);
            double start = period / surfaceLength - blockAlong;
            double end = (period + 1.0D) / surfaceLength - blockAlong;
            List<int[]> strip = clip(clip(polygon, highEdge, start, true), highEdge, end, false);
            if (strip.size() < 3) {
                continue;
            }
            if (strip.size() == 4) {
                result.add(projectStrip(source, strip, highEdge, blockAlong, period,
                        gradient, lowHeight, rise));
            } else {
                for (int i = 1; i < strip.size() - 1; i++) {
                    result.add(projectStrip(source, List.of(strip.getFirst(), strip.get(i),
                                    strip.get(i + 1), strip.get(i + 1)), highEdge, blockAlong,
                            period, gradient, lowHeight, rise));
                }
            }
        }
        return result;
    }

    private static BakedQuad projectStrip(BakedQuad source, List<int[]> strip, Direction highEdge,
                                          int blockAlong, int period, double[] gradient,
                                          float lowHeight, float rise) {
        int[] result = new int[4 * IQuadTransformer.STRIDE];
        float[] normal = slopeNormal(highEdge, rise);
        int packedNormal = packNormal(normal[0], normal[1], normal[2]);
        TextureAtlasSprite sprite = source.getSprite();
        for (int i = 0; i < 4; i++) {
            int[] vertex = strip.get(i);
            int offset = i * IQuadTransformer.STRIDE;
            System.arraycopy(vertex, 0, result, offset, vertex.length);
            float x = Float.intBitsToFloat(vertex[IQuadTransformer.POSITION]);
            float z = Float.intBitsToFloat(vertex[IQuadTransformer.POSITION + 2]);
            double t = progress(highEdge, vertex);
            double phase = Math.clamp(
                    surfaceCoordinate(blockAlong, t, rise) - period, 0.0D, 1.0D);
            result[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(
                    surfaceY(highEdge, lowHeight, rise, x, z));
            float u = Float.intBitsToFloat(vertex[IQuadTransformer.UV0]);
            float v = Float.intBitsToFloat(vertex[IQuadTransformer.UV0 + 1]);
            result[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits((float) Math.clamp(
                    u + (phase - t) * gradient[0], sprite.getU0(), sprite.getU1()));
            result[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits((float) Math.clamp(
                    v + (phase - t) * gradient[1], sprite.getV0(), sprite.getV1()));
            if (IQuadTransformer.NORMAL >= 0) {
                result[offset + IQuadTransformer.NORMAL] = packedNormal;
            }
        }
        return new BakedQuad(result, source.getTintIndex(), Direction.UP, sprite,
                source.isShade(), SLOPE_AMBIENT_OCCLUSION);
    }

    /**
     * Least-squares UV derivative along the slope; works for rotated block sprites.
     */
    private static double[] uvGradient(List<int[]> vertices, Direction direction,
                                       TextureAtlasSprite sprite) {
        double meanT = 0, meanU = 0, meanV = 0;
        for (int[] vertex : vertices) {
            meanT += progress(direction, vertex);
            meanU += Float.intBitsToFloat(vertex[IQuadTransformer.UV0]);
            meanV += Float.intBitsToFloat(vertex[IQuadTransformer.UV0 + 1]);
        }
        meanT /= vertices.size();
        meanU /= vertices.size();
        meanV /= vertices.size();
        double denominator = 0, numeratorU = 0, numeratorV = 0;
        for (int[] vertex : vertices) {
            double deltaT = progress(direction, vertex) - meanT;
            denominator += deltaT * deltaT;
            numeratorU += deltaT * (Float.intBitsToFloat(vertex[IQuadTransformer.UV0]) - meanU);
            numeratorV += deltaT * (Float.intBitsToFloat(vertex[IQuadTransformer.UV0 + 1]) - meanV);
        }
        if (denominator < 0.2D) {
            return null;
        }
        double derivativeU = numeratorU / denominator;
        double derivativeV = numeratorV / denominator;
        // An overlay occupying only part of a sprite cannot be tiled safely.
        if (Math.max(Math.abs(derivativeU) / (sprite.getU1() - sprite.getU0()),
                Math.abs(derivativeV) / (sprite.getV1() - sprite.getV0())) < 0.95D) {
            return null;
        }
        return new double[]{derivativeU, derivativeV};
    }

    private static List<int[]> clip(List<int[]> input, Direction direction,
                                    double boundary, boolean keepGreater) {
        if (input.isEmpty()) {
            return input;
        }
        List<int[]> result = new ArrayList<>(6);
        int[] previous = input.getLast();
        double previousT = progress(direction, previous);
        boolean previousInside = keepGreater ? previousT >= boundary - CLIP_EPSILON
                : previousT <= boundary + CLIP_EPSILON;
        for (int[] current : input) {
            double currentT = progress(direction, current);
            boolean currentInside = keepGreater ? currentT >= boundary - CLIP_EPSILON
                    : currentT <= boundary + CLIP_EPSILON;
            if (currentInside != previousInside) {
                result.add(interpolate(previous, current,
                        (boundary - previousT) / (currentT - previousT)));
            }
            if (currentInside) {
                result.add(current);
            }
            previous = current;
            previousT = currentT;
            previousInside = currentInside;
        }
        return result;
    }

    private static int[] interpolate(int[] a, int[] b, double fraction) {
        int[] result = Arrays.copyOf(a, a.length);
        for (int i : new int[]{IQuadTransformer.POSITION, IQuadTransformer.POSITION + 1,
                IQuadTransformer.POSITION + 2, IQuadTransformer.UV0, IQuadTransformer.UV0 + 1}) {
            result[i] = Float.floatToRawIntBits((float) (Float.intBitsToFloat(a[i])
                    + fraction * (Float.intBitsToFloat(b[i]) - Float.intBitsToFloat(a[i]))));
        }
        for (int i : new int[]{IQuadTransformer.COLOR, IQuadTransformer.UV2}) {
            if (i >= 0) {
                int packed = 0;
                for (int shift = 0; shift < 32; shift += 8) {
                    int byteA = (a[i] >>> shift) & 0xFF;
                    int byteB = (b[i] >>> shift) & 0xFF;
                    packed |= ((int) Math.round(byteA + fraction * (byteB - byteA)) & 0xFF) << shift;
                }
                result[i] = packed;
            }
        }
        return result;
    }

    private static List<BakedQuad> subdivideForAo(
            List<BakedQuad> source,
            Direction highEdge
    ) {
        List<BakedQuad> result = new ArrayList<>(source.size()
                * AO_SLOPE_SEGMENTS * AO_CROSS_SEGMENTS);
        for (BakedQuad quad : source) {
            List<int[]> original = quadPolygon(quad);
            for (int along = 0; along < AO_SLOPE_SEGMENTS; along++) {
                double alongStart = (double) along / AO_SLOPE_SEGMENTS;
                double alongEnd = (double) (along + 1) / AO_SLOPE_SEGMENTS;
                List<int[]> alongStrip = clip(
                        clip(original, highEdge, alongStart, true),
                        highEdge,
                        alongEnd,
                        false
                );
                for (int across = 0; across < AO_CROSS_SEGMENTS; across++) {
                    double acrossStart = (double) across / AO_CROSS_SEGMENTS;
                    double acrossEnd = (double) (across + 1) / AO_CROSS_SEGMENTS;
                    List<int[]> cell = clipAcross(
                            clipAcross(alongStrip, highEdge, acrossStart, true),
                            highEdge,
                            acrossEnd,
                            false
                    );
                    addPolygon(result, quad, cell);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<int[]> quadPolygon(BakedQuad quad) {
        int[] data = quad.getVertices();
        List<int[]> result = new ArrayList<>(4);
        for (int vertex = 0; vertex < 4; vertex++) {
            result.add(Arrays.copyOfRange(
                    data,
                    vertex * IQuadTransformer.STRIDE,
                    (vertex + 1) * IQuadTransformer.STRIDE
            ));
        }
        return result;
    }

    private static List<int[]> clipAcross(
            List<int[]> input,
            Direction highEdge,
            double boundary,
            boolean keepGreater
    ) {
        if (input.isEmpty()) {
            return input;
        }
        List<int[]> result = new ArrayList<>(6);
        int[] previous = input.getLast();
        double previousCoordinate = across(highEdge, previous);
        boolean previousInside = keepGreater
                ? previousCoordinate >= boundary - CLIP_EPSILON
                : previousCoordinate <= boundary + CLIP_EPSILON;
        for (int[] current : input) {
            double currentCoordinate = across(highEdge, current);
            boolean currentInside = keepGreater
                    ? currentCoordinate >= boundary - CLIP_EPSILON
                    : currentCoordinate <= boundary + CLIP_EPSILON;
            if (currentInside != previousInside) {
                result.add(interpolate(previous, current,
                        (boundary - previousCoordinate)
                                / (currentCoordinate - previousCoordinate)));
            }
            if (currentInside) {
                result.add(current);
            }
            previous = current;
            previousCoordinate = currentCoordinate;
            previousInside = currentInside;
        }
        return result;
    }

    private static double across(Direction highEdge, int[] vertex) {
        int component = highEdge.getAxis() == Direction.Axis.X
                ? IQuadTransformer.POSITION + 2
                : IQuadTransformer.POSITION;
        return Float.intBitsToFloat(vertex[component]);
    }

    private static void addPolygon(
            List<BakedQuad> output,
            BakedQuad source,
            List<int[]> polygon
    ) {
        if (polygon.size() < 3) {
            return;
        }
        if (polygon.size() == 4) {
            output.add(copyQuad(source, polygon));
            return;
        }
        for (int index = 1; index < polygon.size() - 1; index++) {
            output.add(copyQuad(source, List.of(
                    polygon.getFirst(),
                    polygon.get(index),
                    polygon.get(index + 1),
                    polygon.get(index + 1)
            )));
        }
    }

    private static BakedQuad copyQuad(BakedQuad source, List<int[]> polygon) {
        int[] vertices = new int[4 * IQuadTransformer.STRIDE];
        for (int vertex = 0; vertex < 4; vertex++) {
            System.arraycopy(polygon.get(vertex), 0, vertices,
                    vertex * IQuadTransformer.STRIDE, IQuadTransformer.STRIDE);
        }
        return new BakedQuad(
                vertices,
                source.getTintIndex(),
                source.getDirection(),
                source.getSprite(),
                source.isShade(),
                SLOPE_AMBIENT_OCCLUSION
        );
    }

    static double surfaceCoordinate(int blockAlong, double progress) {
        return surfaceCoordinate(blockAlong, progress, false);
    }

    private static double surfaceCoordinate(
            int blockAlong,
            double progress,
            boolean fullHeight
    ) {
        return surfaceCoordinate(blockAlong, progress, fullHeight ? 1F : 0.5F);
    }

    private static double surfaceCoordinate(int blockAlong, double progress, float rise) {
        return (blockAlong + progress) * Math.sqrt(1D + rise * rise);
    }

    private static double progress(Direction direction, int[] vertex) {
        float x = Float.intBitsToFloat(vertex[IQuadTransformer.POSITION]);
        float z = Float.intBitsToFloat(vertex[IQuadTransformer.POSITION + 2]);
        return switch (direction) {
            case SOUTH -> z;
            case NORTH -> 1.0F - z;
            case EAST -> x;
            case WEST -> 1.0F - x;
            default -> throw new IllegalArgumentException("Ramp direction must be horizontal");
        };
    }

    private void addBoundaryFaces(
            List<BakedQuad> output,
            @Nullable BakedQuad fallbackMaterial,
            Direction highEdge,
            int joined,
            BakedModel sourceModel,
            Source source,
            ModelData sourceData,
            RenderType renderType,
            long seed
            // , @Nullable List<BakedQuad> continuityQuads
    ) {
        List<Face> faces = boundaryFaces(joined);
        for (Face face : faces) {
            float[] normal = rotateNormalFromSouth(face.normal(), highEdge);
            Direction sourceFace = Direction.getNearest(normal[0], normal[1], normal[2]);
//             List<BakedQuad> materials = continuityQuads != null
//                     ? filterFaceQuads(continuityQuads, sourceFace)
//                     : sourceFaceQuads(sourceModel, source, sourceData,
//                     renderType, seed, sourceFace);
            List<BakedQuad> materials = sourceFaceQuads(
                    sourceModel, source, sourceData, renderType, seed, sourceFace);
            if (materials.isEmpty() && fallbackMaterial != null) {
                materials = List.of(fallbackMaterial);
            }
            for (BakedQuad material : materials) {
                Vertex[] transformed = projectBoundaryVertices(face, highEdge, material,
                        followsSlopeEdge(material));
                output.add(bakeQuad(transformed, normal, material.getSprite(),
                        material.getTintIndex(), material.isShade(), false));
            }
        }
    }

    private List<BakedQuad> fallbackGeometry(
            TextureAtlasSprite sprite,
            Direction highEdge,
            int joined,
            SlopeAmbientOcclusion slopeAo
    ) {
        BakedQuad rawSlope = bakeQuad(
                Arrays.stream(fullSlope()).map(vertex -> new Vertex(vertex.x(), lowHeight + rise * vertex.y(), vertex.z(), vertex.u(), vertex.v()))
                        .map(vertex -> rotateFromSouth(vertex, highEdge))
                        .toArray(Vertex[]::new),
                slopeNormal(highEdge, rise),
                sprite,
                -1,
                true,
                SLOPE_AMBIENT_OCCLUSION
        );
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad slope : subdivideForAo(List.of(rawSlope), highEdge)) {
            result.add(withSlopeAo(slope, slopeAo));
        }
        List<Face> faces = boundaryFaces(joined);
        for (Face face : faces) {
            result.add(bakeQuad(
                    Arrays.stream(face.vertices())
                            .map(vertex -> projectBoundaryVertex(vertex, face.normal(), highEdge))
                            .toArray(Vertex[]::new),
                    rotateNormalFromSouth(face.normal(), highEdge),
                    sprite,
                    -1,
                    true,
                    false
            ));
        }
        return List.copyOf(result);
    }

    private static RampAoQuad withSlopeAo(
            BakedQuad quad,
            SlopeAmbientOcclusion slopeAo
    ) {
        float[] visibility = new float[4];
        int[] vertices = quad.getVertices();
        for (int vertex = 0; vertex < visibility.length; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            visibility[vertex] = slopeAo.sample(
                    Float.intBitsToFloat(vertices[offset]),
                    Float.intBitsToFloat(vertices[offset + 1]),
                    Float.intBitsToFloat(vertices[offset + 2])
            );
        }
        return RampAoQuad.copyOf(quad, visibility);
    }

    private List<Face> boundaryFaces(int joined) {
        List<Face> source = lowHeight > 0 ? upperFaces() : fullFaces();
        List<Face> result = new ArrayList<>();
        for (Face face : source) {
            if (face.normal()[2] > 0 && (joined & 1) != 0) continue;
            if (face.normal()[2] < 0 && (joined & 2) != 0) continue;
            Vertex[] vertices = Arrays.stream(face.vertices()).map(vertex -> {
                float y = lowHeight > 0
                        ? vertex.y() == 0 ? 0 : lowHeight + (vertex.y() - 0.5F) * 2 * rise
                        : vertex.y() * rise;
                return new Vertex(vertex.x(), y, vertex.z(), vertex.u(), vertex.v());
            }).toArray(Vertex[]::new);
            result.add(face(face.normal(), vertices));
        }
        return result;
    }

    private static Vertex projectBoundaryVertex(Vertex vertex, float[] normal, Direction highEdge) {
        Vertex rotated = rotateFromSouth(vertex, highEdge);
        if (normal[1] != 0.0F) {
            return rotated;
        }
        return new Vertex(rotated.x(), rotated.y(), rotated.z(), rotated.u(),
                sideUvAtHeight(rotated.y()));
    }

    static float sideUvAtHeight(float height) {
        return 1.0F - height;
    }

    private Vertex[] projectBoundaryVertices(Face face, Direction highEdge,
                                             BakedQuad material, boolean slopeEdge) {
        float[] rotatedNormal = rotateNormalFromSouth(face.normal(), highEdge);
        Direction sourceFace = Direction.getNearest(rotatedNormal[0], rotatedNormal[1],
                rotatedNormal[2]);
        UvMapping mapping = UvMapping.from(material, sourceFace);
        return Arrays.stream(face.vertices()).map(vertex -> {
            Vertex rotated = rotateFromSouth(vertex, highEdge);
            if (mapping == null) {
                return projectBoundaryVertex(vertex, face.normal(), highEdge);
            }
            float sampleY = rotated.y();
            if (slopeEdge && sourceFace.getAxis().isHorizontal()) {
                float surface = surfaceY(
                        highEdge, lowHeight, rise, rotated.x(), rotated.z());
                sampleY = overlaySampleY(surface, rotated.y());
            }
            float[] uv = mapping.sample(rotated.x(), sampleY, rotated.z());
            return new Vertex(rotated.x(), rotated.y(), rotated.z(), uv[0], uv[1]);
        }).toArray(Vertex[]::new);
    }

    static float overlaySampleY(float surfaceY, float vertexY) {
        return 1.0F - (surfaceY - vertexY);
    }

    private static boolean followsSlopeEdge(BakedQuad quad) {
        String path = quad.getSprite().contents().name().getPath();
        return path.endsWith("grass_block_side_overlay");
    }

    private static BakedQuad bakeQuad(
            Vertex[] vertices,
            float[] normal,
            TextureAtlasSprite sprite,
            int tint,
            boolean shade,
            boolean ambientOcclusion
    ) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setTintIndex(tint);
        builder.setDirection(Direction.getNearest(normal[0], normal[1], normal[2]));
        builder.setShade(shade);
        builder.setHasAmbientOcclusion(ambientOcclusion);
        for (Vertex vertex : vertices) {
            builder.addVertex(vertex.x(), vertex.y(), vertex.z())
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(vertex.u()), sprite.getV(vertex.v()))
                    .setNormal(normal[0], normal[1], normal[2]);
        }
        return builder.bakeQuad();
    }

    private Source findSource(BlockAndTintGetter level, BlockPos rampPosition) {
        BlockPos.MutableBlockPos cursor = rampPosition.mutable().move(Direction.DOWN);
        for (int depth = 0;
             depth < MAXIMUM_SOURCE_DEPTH && cursor.getY() >= level.getMinBuildHeight();
             depth++, cursor.move(Direction.DOWN)) {
            BlockState state = level.getBlockState(cursor);
            if (ModBlocks.isMimicRampId(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                continue;
            }
            return state.isAir() ? null : new Source(cursor.immutable(), state);
        }
        return null;
    }

    static float surfaceY(Direction highEdge, boolean upper, float x, float z) {
        return surfaceY(highEdge, upper, false, x, z);
    }

    static float surfaceY(
            Direction highEdge,
            boolean upper,
            boolean fullHeight,
            float x,
            float z
    ) {
        float progress = switch (highEdge) {
            case SOUTH -> z;
            case NORTH -> 1.0F - z;
            case EAST -> x;
            case WEST -> 1.0F - x;
            default -> throw new IllegalArgumentException("Ramp direction must be horizontal");
        };
        return fullHeight ? progress : (upper ? 0.5F : 0.0F) + progress * 0.5F;
    }

    private static float[] slopeNormal(Direction highEdge) {
        return slopeNormal(highEdge, false);
    }

    static float surfaceY(Direction highEdge, float lowHeight, float rise, float x, float z) {
        return 0.5F + RampCollisionGeometry.surfaceY(
                highEdge, x - 0.5F, z - 0.5F, lowHeight, rise);
    }

    private static float[] slopeNormal(Direction highEdge, float rise) {
        var normal = RampCollisionGeometry.surfaceNormal(highEdge, rise);
        return new float[]{normal.x, normal.y, normal.z};
    }

    private static float[] slopeNormal(Direction highEdge, boolean fullHeight) {
        float rise = fullHeight ? 1.0F : 0.5F;
        float x = -rise * highEdge.getStepX();
        float z = -rise * highEdge.getStepZ();
        float inverseLength = 1.0F / (float) Math.sqrt(x * x + 1.0F + z * z);
        return new float[]{x * inverseLength, inverseLength, z * inverseLength};
    }

    private static int packNormal(float x, float y, float z) {
        return ((int) (x * 127.0F) & 0xFF)
                | (((int) (y * 127.0F) & 0xFF) << 8)
                | (((int) (z * 127.0F) & 0xFF) << 16);
    }

    private static Vertex rotateFromSouth(Vertex vertex, Direction highEdge) {
        return switch (highEdge) {
            case SOUTH -> vertex;
            case EAST -> new Vertex(vertex.z(), vertex.y(), 1.0F - vertex.x(),
                    vertex.u(), vertex.v());
            case NORTH -> new Vertex(1.0F - vertex.x(), vertex.y(), 1.0F - vertex.z(),
                    vertex.u(), vertex.v());
            case WEST -> new Vertex(1.0F - vertex.z(), vertex.y(), vertex.x(),
                    vertex.u(), vertex.v());
            default -> throw new IllegalArgumentException("Ramp direction must be horizontal");
        };
    }

    private static float[] rotateNormalFromSouth(float[] normal, Direction highEdge) {
        return switch (highEdge) {
            case SOUTH -> normal;
            case EAST -> new float[]{normal[2], normal[1], -normal[0]};
            case NORTH -> new float[]{-normal[0], normal[1], -normal[2]};
            case WEST -> new float[]{-normal[2], normal[1], normal[0]};
            default -> throw new IllegalArgumentException("Ramp direction must be horizontal");
        };
    }

    private static Vertex[] lowerSlope() {
        return new Vertex[]{
                vertex(0, 0, 0, 0, 1), vertex(0, 0.5F, 1, 0, 0),
                vertex(1, 0.5F, 1, 1, 0), vertex(1, 0, 0, 1, 1)
        };
    }

    private static Vertex[] upperSlope() {
        return new Vertex[]{
                vertex(0, 1, 1, 0, 0), vertex(1, 1, 1, 1, 0),
                vertex(1, 0.5F, 0, 1, 1), vertex(0, 0.5F, 0, 0, 1)
        };
    }

    private static Vertex[] fullSlope() {
        return new Vertex[]{
                vertex(0, 0, 0, 0, 1), vertex(0, 1, 1, 0, 0),
                vertex(1, 1, 1, 1, 0), vertex(1, 0, 0, 1, 1)
        };
    }

    private static List<Face> lowerFaces() {
        return List.of(
                face(new float[]{1, 0, 0},
                        vertex(1, 0.5F, 1, 0, 0), vertex(1, 0, 1, 0, 1),
                        vertex(1, 0, 0, 1, 1), vertex(1, 0, 0, 1, 1)),
                face(new float[]{-1, 0, 0},
                        vertex(0, 0, 1, 0, 1), vertex(0, 0.5F, 1, 0, 0),
                        vertex(0, 0, 0, 1, 1), vertex(0, 0, 0, 1, 1)),
                face(new float[]{0, -1, 0},
                        vertex(1, 0, 0, 1, 1), vertex(1, 0, 1, 1, 0),
                        vertex(0, 0, 1, 0, 0), vertex(0, 0, 0, 0, 1)),
                face(new float[]{0, 0, 1},
                        vertex(1, 0, 1, 1, 1), vertex(1, 0.5F, 1, 1, 0),
                        vertex(0, 0.5F, 1, 0, 0), vertex(0, 0, 1, 0, 1))
        );
    }

    private static List<Face> upperFaces() {
        return List.of(
                face(new float[]{0, -1, 0},
                        vertex(1, 0, 0, 1, 1), vertex(1, 0, 1, 1, 0),
                        vertex(0, 0, 1, 0, 0), vertex(0, 0, 0, 0, 1)),
                face(new float[]{0, 0, -1},
                        vertex(0, 0.5F, 0, 0, 0), vertex(1, 0.5F, 0, 1, 0),
                        vertex(1, 0, 0, 1, 1), vertex(0, 0, 0, 0, 1)),
                face(new float[]{0, 0, 1},
                        vertex(1, 0, 1, 1, 1), vertex(1, 1, 1, 1, 0),
                        vertex(0, 1, 1, 0, 0), vertex(0, 0, 1, 0, 1)),
                face(new float[]{-1, 0, 0},
                        vertex(0, 0, 1, 1, 1), vertex(0, 1, 1, 1, 0),
                        vertex(0, 0.5F, 0, 0, 0), vertex(0, 0, 0, 0, 1)),
                face(new float[]{1, 0, 0},
                        vertex(1, 0.5F, 0, 0, 0), vertex(1, 1, 1, 1, 0),
                        vertex(1, 0, 1, 1, 1), vertex(1, 0, 0, 0, 1))
        );
    }

    private static List<Face> fullFaces() {
        return List.of(
                face(new float[]{1, 0, 0},
                        vertex(1, 1, 1, 0, 0), vertex(1, 0, 1, 0, 1),
                        vertex(1, 0, 0, 1, 1), vertex(1, 0, 0, 1, 1)),
                face(new float[]{-1, 0, 0},
                        vertex(0, 0, 1, 0, 1), vertex(0, 1, 1, 0, 0),
                        vertex(0, 0, 0, 1, 1), vertex(0, 0, 0, 1, 1)),
                face(new float[]{0, -1, 0},
                        vertex(1, 0, 0, 1, 1), vertex(1, 0, 1, 1, 0),
                        vertex(0, 0, 1, 0, 0), vertex(0, 0, 0, 0, 1)),
                face(new float[]{0, 0, 1},
                        vertex(1, 0, 1, 1, 1), vertex(1, 1, 1, 1, 0),
                        vertex(0, 1, 1, 0, 0), vertex(0, 0, 1, 0, 1))
        );
    }

    private static Vertex vertex(float x, float y, float z, float u, float v) {
        return new Vertex(x, y, z, u, v);
    }

    private static Face face(float[] normal, Vertex... vertices) {
        return new Face(normal, vertices);
    }

    private record Source(BlockPos position, BlockState state) {
    }

    private record Appearance(
            Map<RenderType, List<BakedQuad>> quads,
            TextureAtlasSprite particle
    ) {
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }

    private record Face(float[] normal, Vertex[] vertices) {
    }

    /**
     * Affine UV map recovered from the source model's already-baked face.
     */
    private record UvMapping(
            Direction face,
            double uP,
            double uQ,
            double uC,
            double vP,
            double vQ,
            double vC
    ) {
        @Nullable
        static UvMapping from(BakedQuad quad, Direction face) {
            int[] data = quad.getVertices();
            if (data.length != 4 * IQuadTransformer.STRIDE || IQuadTransformer.UV0 < 0) {
                return null;
            }
            double[][] samples = new double[4][4];
            TextureAtlasSprite sprite = quad.getSprite();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE;
                float x = Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION]);
                float y = Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 1]);
                float z = Float.intBitsToFloat(data[offset + IQuadTransformer.POSITION + 2]);
                double[] point = coordinates(face, x, y, z);
                samples[vertex][0] = point[0];
                samples[vertex][1] = point[1];
                samples[vertex][2] = sprite.getUOffset(Float.intBitsToFloat(
                        data[offset + IQuadTransformer.UV0]));
                samples[vertex][3] = sprite.getVOffset(Float.intBitsToFloat(
                        data[offset + IQuadTransformer.UV0 + 1]));
            }
            for (int first = 0; first < 2; first++) {
                for (int second = first + 1; second < 3; second++) {
                    for (int third = second + 1; third < 4; third++) {
                        double p10 = samples[second][0] - samples[first][0];
                        double q10 = samples[second][1] - samples[first][1];
                        double p20 = samples[third][0] - samples[first][0];
                        double q20 = samples[third][1] - samples[first][1];
                        double determinant = p10 * q20 - p20 * q10;
                        if (Math.abs(determinant) < 1.0E-7D) {
                            continue;
                        }
                        double[] u = solve(samples, first, second, third, 2, determinant);
                        double[] v = solve(samples, first, second, third, 3, determinant);
                        return new UvMapping(face, u[0], u[1], u[2], v[0], v[1], v[2]);
                    }
                }
            }
            return null;
        }

        private static double[] solve(double[][] samples, int first, int second,
                                      int third, int value, double determinant) {
            double p10 = samples[second][0] - samples[first][0];
            double q10 = samples[second][1] - samples[first][1];
            double p20 = samples[third][0] - samples[first][0];
            double q20 = samples[third][1] - samples[first][1];
            double value10 = samples[second][value] - samples[first][value];
            double value20 = samples[third][value] - samples[first][value];
            double pCoefficient = (value10 * q20 - value20 * q10) / determinant;
            double qCoefficient = (p10 * value20 - p20 * value10) / determinant;
            double constant = samples[first][value]
                    - pCoefficient * samples[first][0]
                    - qCoefficient * samples[first][1];
            return new double[]{pCoefficient, qCoefficient, constant};
        }

        float[] sample(float x, float y, float z) {
            double[] point = coordinates(face, x, y, z);
            return new float[]{
                    (float) (uP * point[0] + uQ * point[1] + uC),
                    (float) (vP * point[0] + vQ * point[1] + vC)
            };
        }

        private static double[] coordinates(Direction face, float x, float y, float z) {
            return switch (face.getAxis()) {
                case X -> new double[]{z, y};
                case Y -> new double[]{x, z};
                case Z -> new double[]{x, y};
            };
        }
    }
}
