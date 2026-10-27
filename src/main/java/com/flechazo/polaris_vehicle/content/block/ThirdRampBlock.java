package com.flechazo.polaris_vehicle.content.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
public final class ThirdRampBlock extends RampBlock {
    public static final MapCodec<ThirdRampBlock> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(propertiesCodec(),
                    Codec.intRange(0, 2).fieldOf("segment")
                            .forGetter(block -> block.segment)).apply(instance, ThirdRampBlock::new));
    private final int segment;
    private final Map<Direction, VoxelShape> shapes;

    public ThirdRampBlock(BlockBehaviour.Properties properties, int segment) {
        super(properties);
        if (segment < 0 || segment > 2) throw new IllegalArgumentException("Invalid ramp segment " + segment);
        this.segment = segment;
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL)
            shapes.put(facing, new RampShape(facing, lowHeight(), rise()));
        this.shapes = Map.copyOf(shapes);
    }

    @Override
    public MapCodec<ThirdRampBlock> codec() {
        return CODEC;
    }

    @Override
    public float lowHeight() {
        return segment / 3F;
    }

    @Override
    public float rise() {
        return 1F / 3F;
    }

    @Override
    protected VoxelShape shapeFor(BlockState state) {
        return shapes.get(state.getValue(FACING));
    }
}
