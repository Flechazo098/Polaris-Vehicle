package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public final class VehiclePowerUnitItem extends VehiclePartItem implements GeoItem, StaticGeoAsset {
    private final VehiclePowerDefinition definition;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehiclePowerUnitItem(Properties properties, VehiclePowerDefinition definition) {
        super(properties, definition.id(), definition.category(), 1);
        this.definition = definition;
    }

    public VehiclePowerDefinition definition() {
        return definition;
    }

    @Override
    public String geckoAssetFolder() {
        return "item";
    }

    @Override
    public String geckoAssetName() {
        return definition.id().getPath() + "_model";
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<VehiclePowerUnitItem>install(consumer);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.power.normal_modifiers",
                definition.normalMaxSpeedModifier(),
                definition.normalAccelerationModifier()
        ).withStyle(ChatFormatting.GRAY));
        if (!VehicleTooltips.details(tooltip, flag)) return;
        if (definition.isExtraPowerUnit()) {
            tooltip.add(Component.translatable(
                    "tooltip.polaris_vehicle.power.boost",
                    definition.energyCost(),
                    definition.boostDurationTicks() / 20.0,
                    definition.boostMaxSpeedModifier(),
                    definition.boostAccelerationModifier()
            ).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                    "tooltip.polaris_vehicle.power.capacity",
                    definition.energyCapacity()
            ).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.polaris_vehicle.power.recovery",
                    definition.passiveChargePerSecond()
            ).withStyle(ChatFormatting.AQUA));
        }
    }
}
