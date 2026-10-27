package com.flechazo.polaris_vehicle.content.block;

import com.flechazo.polaris_vehicle.content.block.entity.FuelExtractorBlockEntity;
import com.flechazo.polaris_vehicle.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class FuelExtractorBlock extends BaseEntityBlock {
    public static final MapCodec<FuelExtractorBlock> CODEC = simpleCodec(FuelExtractorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final float MODEL_SCALE = 0.84F;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            enlargedBox(8, 0.8, 4, 16, 9.6, 12),
            enlargedBox(-0.8, 0, 3.2, 16.8, 0.8, 12.8),
            enlargedBox(8.8, 6.4, 3.92, 15.2, 8, 4.72),
            enlargedBox(11.2, 2.4, 3.72, 12.8, 4, 4.52),
            enlargedBox(10.8, 9.6, 6.8, 13.2, 10.4, 9.2),
            enlargedBox(11.2, 10.4, 7.2, 12.8, 12.8, 8.8),
            enlargedBox(3.2, 11.2, 7.2, 11.2, 12.8, 8.8),
            enlargedBox(0, 0.8, 4, 8, 7.2, 12),
            enlargedBox(2.8, 7.2, 6.8, 5.2, 8, 9.2),
            enlargedBox(3.2, 8, 7.2, 4.8, 11.2, 8.8));
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    private static VoxelShape enlargedBox(double x0, double y0, double z0, double x1, double y1, double z1) {
        return box(8 + (x0 - 8) * 1.05, y0 * 1.05, 8 + (z0 - 8) * 1.05,
                8 + (x1 - 8) * 1.05, y1 * 1.05, 8 + (z1 - 8) * 1.05);
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        result.put(Direction.NORTH, NORTH_SHAPE);
        for (Direction facing : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape shape = Shapes.empty();
            for (var box : NORTH_SHAPE.toAabbs()) {
                double x0, x1, z0, z1;
                if (facing == Direction.EAST) {
                    x0 = 1 - box.maxZ;
                    x1 = 1 - box.minZ;
                    z0 = box.minX;
                    z1 = box.maxX;
                } else if (facing == Direction.WEST) {
                    x0 = box.minZ;
                    x1 = box.maxZ;
                    z0 = 1 - box.maxX;
                    z1 = 1 - box.minX;
                } else {
                    x0 = 1 - box.maxX;
                    x1 = 1 - box.minX;
                    z0 = 1 - box.maxZ;
                    z1 = 1 - box.minZ;
                }
                shape = Shapes.or(shape, Shapes.box(x0, box.minY, z0, x1, box.maxY, z1));
            }
            result.put(facing, shape.optimize());
        }
        return Map.copyOf(result);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    public FuelExtractorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelExtractorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                type,
                ModBlockEntities.FUEL_EXTRACTOR.get(),
                FuelExtractorBlockEntity::serverTick
        );
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof FuelExtractorBlockEntity extractor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        extractor.openMenu(player);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FuelExtractorBlockEntity extractor) {
            extractor.openMenu(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof FuelExtractorBlockEntity extractor) {
            Containers.dropContents(level, pos, extractor);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
