package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.client.renderer.StaticGeoItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

import java.util.function.Consumer;

public final class StaticGeoProvider {

    public static <T extends Item & GeoAnimatable & StaticGeoAsset> void install(
            Consumer<GeoRenderProvider> consumer
    ) {
        consumer.accept(new GeoRenderProvider() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new StaticGeoItemRenderer<T>();
                }
                return renderer;
            }
        });
    }
}
