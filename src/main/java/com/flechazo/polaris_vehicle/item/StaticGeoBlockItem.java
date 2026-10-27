package com.flechazo.polaris_vehicle.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public final class StaticGeoBlockItem extends BlockItem implements GeoItem, StaticGeoAsset {
    private final String assetName;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public StaticGeoBlockItem(Block block, Properties properties, String assetName) {
        super(block, properties);
        this.assetName = assetName;
    }

    @Override
    public String geckoAssetFolder() {
        return "block";
    }

    @Override
    public String geckoAssetName() {
        return assetName;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<StaticGeoBlockItem>install(consumer);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
