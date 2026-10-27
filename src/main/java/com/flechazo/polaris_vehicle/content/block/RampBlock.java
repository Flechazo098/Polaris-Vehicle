package com.flechazo.polaris_vehicle.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class RampBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<RampBlock> CODEC = simpleCodec(RampBlock::new);
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = shapes(false);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = shapes(true);
    private static final Map<Direction, VoxelShape> FULL_SHAPES = fullShapes();
    private final boolean upper;
    private final boolean fullHeight;

    public RampBlock(BlockBehaviour.Properties properties) {
        this(properties, false, false);
    }

    public RampBlock(BlockBehaviour.Properties properties, boolean upper) {
        this(properties, upper, false);
    }

    protected RampBlock(
            BlockBehaviour.Properties properties,
            boolean upper,
            boolean fullHeight
    ) {
        super(properties);
        if (upper && fullHeight) {
            throw new IllegalArgumentException("Ramp cannot be upper and full-height");
        }
        this.upper = upper;
        this.fullHeight = fullHeight;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    public MapCodec<? extends RampBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    public float lowHeight() {
        return upper ? 0.5F : 0F;
    }

    public float rise() {
        return fullHeight ? 1F : 0.5F;
    }

    public boolean upper() {
        return upper;
    }

    public boolean fullHeight() {
        return fullHeight;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context
    ) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context
    ) {
        return shapeFor(state);
    }

    protected VoxelShape shapeFor(BlockState state) {
        return (fullHeight ? FULL_SHAPES : upper ? UPPER_SHAPES : LOWER_SHAPES)
                .get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> shapes(boolean upper) {
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result.put(direction, new RampShape(direction, upper));
        }
        return Map.copyOf(result);
    }

    private static Map<Direction, VoxelShape> fullShapes() {
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result.put(direction, new RampShape(direction, false, true));
        }
        return Map.copyOf(result);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

}
