package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinition;
import com.flechazo.polaris_vehicle.part.VehiclePartWear;
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
import java.util.Locale;
import java.util.function.Consumer;

public final class VehicleEngineItem extends VehiclePartItem implements GeoItem, StaticGeoAsset {
    private final VehicleEngineDefinition definition;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehicleEngineItem(Properties properties, VehicleEngineDefinition definition) {
        super(properties, definition.id(), definition.category(), definition.maxDurability());
        this.definition = definition;
    }

    public VehicleEngineDefinition definition() {
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
        StaticGeoProvider.<VehicleEngineItem>install(consumer);
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
        super.appendHoverText(stack, context, tooltip, flag);
        InstalledPart part = readPart(stack);
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.engine_horsepower",
                decimal(definition.effectiveHorsepower(part))
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.engine_acceleration",
                decimal(definition.effectiveAcceleration(part))
        ).withStyle(ChatFormatting.GRAY));
        if (!VehicleTooltips.details(tooltip, flag)) return;
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.part_wear_stage",
                VehiclePartWear.crossedThresholds(part)
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
