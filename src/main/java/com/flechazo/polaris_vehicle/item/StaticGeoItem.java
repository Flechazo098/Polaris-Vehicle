package com.flechazo.polaris_vehicle.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class StaticGeoItem extends Item implements GeoItem, StaticGeoAsset {
    private final String assetName;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public StaticGeoItem(Properties properties, String assetName) {
        super(properties);
        this.assetName = assetName;
    }

    @Override
    public final String geckoAssetFolder() {
        return "item";
    }

    @Override
    public final String geckoAssetName() {
        return assetName;
    }

    @Override
    public final void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<StaticGeoItem>install(consumer);
    }

    @Override
    public final AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public boolean isPerspectiveAware() {
        return usesAuthoredHandPose();
    }

    @Override
    public double getBoneResetTime() {
        return 0.0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (!usesAuthoredHandPose()) {
            return;
        }
        String clip = "animation." + geckoAnimationName();
        RawAnimation firstPerson = RawAnimation.begin().thenLoop(clip + ".first_person");
        RawAnimation thirdPerson = RawAnimation.begin().thenLoop(clip + ".third_person");
        controllers.add(new AnimationController<>(
                this,
                "display_pose",
                0,
                state -> {
                    ItemDisplayContext context = state.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
                    if (context == null) {
                        return PlayState.STOP;
                    }
                    if (context.firstPerson()) {
                        return state.setAndContinue(firstPerson);
                    }
                    if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                            || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                        return state.setAndContinue(thirdPerson);
                    }
                    return PlayState.STOP;
                }
        ));
    }
}
