package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FixedRampModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Lighting> LIGHTING = new ModelProperty<>();

    private record Lighting(SlopeAmbientOcclusion ao, @Nullable Direction join, float coveredHeight,
                            @Nullable Direction secondJoin, float secondHeight) {
    }

    public FixedRampModel(BakedModel model) {
        super(model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos position, BlockState state, ModelData data) {
        RampBlock ramp = (RampBlock) state.getBlock();
        Direction highEdge = state.getValue(RampBlock.FACING);
        Direction join = null;
        Direction secondJoin = null;
        float covered = 0, secondHeight = 0;
        for (Direction edge : new Direction[]{highEdge, highEdge.getOpposite()}) {
            BlockState neighbor = level.getBlockState(position.relative(edge));
            if (neighbor.getBlock() instanceof RampBlock other
                    && ModBlocks.matchingFixedRampMaterial(ramp, other)
                    && neighbor.getValue(RampBlock.FACING) == highEdge
                    && Math.abs(ramp.rise() - other.rise()) < 0.00001F
                    && Math.abs((edge == highEdge ? ramp.lowHeight() + ramp.rise() : ramp.lowHeight())
                    - (edge == highEdge ? other.lowHeight() : other.lowHeight() + other.rise())) < 0.00001F) {
                float height = edge == highEdge ? ramp.lowHeight() + ramp.rise() : ramp.lowHeight();
                if (join == null) {
                    join = edge;
                    covered = height;
                } else {
                    secondJoin = edge;
                    secondHeight = height;
                }
            }
        }
        return originalModel.getModelData(level, position, state, data).derive()
                .with(LIGHTING, new Lighting(new SlopeAmbientOcclusion(level, position, highEdge, ramp.rise()),
                        join, covered, secondJoin, secondHeight)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random, ModelData data, @Nullable RenderType type) {
        List<BakedQuad> source = originalModel.getQuads(state, side, random, data, type);
        Lighting lighting = data.get(LIGHTING);
        if (state == null || lighting == null) return source;
        SlopeAmbientOcclusion ao = lighting.ao();
        Direction join = lighting.join();
        return source.stream().filter(quad -> !sharedLowerWall(quad, join, lighting.coveredHeight())
                && !sharedLowerWall(quad, lighting.secondJoin(), lighting.secondHeight())).map(quad -> {
            int[] vertices = quad.getVertices();
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (int vertex = 0; vertex < 4; vertex++) {
                float y = Float.intBitsToFloat(vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1]);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            // Only the sloping top receives slope AO; vertical boundary faces
            // keep their original OBJ light behaviour and exact authored UVs.
            if (quad.getDirection() != Direction.UP || maxY - minY < 0.01F) return quad;
            float[] visibility = new float[4];
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                visibility[vertex] = ao.sample(Float.intBitsToFloat(vertices[offset]),
                        Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2]));
            }
            return RampAoQuad.copyOf(quad, visibility);
        }).toList();
    }

    private static boolean sharedLowerWall(BakedQuad quad, @Nullable Direction join, float height) {
        if (join == null || quad.getDirection() != join) return false;
        int[] vertices = quad.getVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float edge = Float.intBitsToFloat(vertices[offset + (join.getAxis() == Direction.Axis.X ? 0 : 2)]);
            if (y > height + 0.00001F || Math.abs(edge - (join.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) > 0.00001F)
                return false;
        }
        return true;
    }
}
