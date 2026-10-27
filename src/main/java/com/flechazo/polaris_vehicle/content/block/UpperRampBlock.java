package com.flechazo.polaris_vehicle.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;
public final class UpperRampBlock extends RampBlock {
    public static final MapCodec<UpperRampBlock> CODEC = simpleCodec(UpperRampBlock::new);

    public UpperRampBlock(BlockBehaviour.Properties properties) {
        super(properties, true);
    }

    @Override
    public MapCodec<UpperRampBlock> codec() {
        return CODEC;
    }
}
