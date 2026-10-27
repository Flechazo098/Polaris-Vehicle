package com.flechazo.polaris_vehicle.item;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.client.renderer.CrewDollRenderer;
import com.flechazo.polaris_vehicle.content.entity.CrewDollEntity;
import com.flechazo.polaris_vehicle.registry.ModEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public final class CrewDollItem extends Item implements GeoItem {
    private static final Vec3 PLACEMENT_OFFSET = Vec3.ZERO;
    private final CrewDollVariant variant;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public CrewDollItem(Properties properties, CrewDollVariant variant) {
        super(properties);
        this.variant = variant;
    }

    public CrewDollVariant variant() {
        return variant;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        return Pathway.nullable(ModEntityTypes.CREW_DOLL.get().create(serverLevel))
                .map(doll -> placeDoll(context, serverLevel, doll))
                .getOrElse(InteractionResult.FAIL);
    }

    private InteractionResult placeDoll(UseOnContext context, ServerLevel serverLevel, CrewDollEntity doll) {
        BlockPos placementBlock = context.getClickedPos().relative(context.getClickedFace());
        Vec3 placement = Vec3.atBottomCenterOf(placementBlock).add(PLACEMENT_OFFSET);
        doll.setVariant(variant);
        doll.setPos(placement);
        Pathway.nullable(context.getPlayer()).peek(player -> doll.setYRot(player.getYRot())).run();
        doll.setOldPosAndRot();
        if (!serverLevel.noCollision(doll)
                || !serverLevel.getWorldBorder().isWithinBounds(doll.getBoundingBox())
                || !serverLevel.addFreshEntity(doll)) {
            doll.discard();
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.literal(
                variant.officialName()
        ).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private CrewDollRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new CrewDollRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (variant == CrewDollVariant.YELLOW_DUCK) {
            controllers.add(new AnimationController<>(this, "duck_scale", 0,
                    state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.unknown.scale"))));
        }
    }
}
