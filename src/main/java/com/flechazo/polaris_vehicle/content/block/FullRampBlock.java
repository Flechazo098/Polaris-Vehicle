package com.flechazo.polaris_vehicle.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class FullRampBlock extends RampBlock {
    public static final MapCodec<FullRampBlock> CODEC = simpleCodec(FullRampBlock::new);

    public FullRampBlock(BlockBehaviour.Properties properties) {
        super(properties, false, true);
    }

    @Override
    public MapCodec<FullRampBlock> codec() {
        return CODEC;
    }
}
