package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePaintDefinitions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public final class VehiclePaintItem extends Item implements GeoItem, StaticGeoAsset {
    private static final String ASSET_NAME = "spray_paint_can";

    private final VehiclePaintDefinition paint;
    private final String subtitleKey;
    private final String canTexture;
    private final List<Component> details;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehiclePaintItem(Properties properties, VehiclePaintDefinition paint) {
        this(properties, paint, null, ASSET_NAME + paint.textureSuffix() + "_model");
    }

    public VehiclePaintItem(Properties properties, VehiclePaintDefinition paint, String subtitleKey, String canTexture) {
        this(properties, paint, subtitleKey, canTexture, List.of());
    }

    public VehiclePaintItem(
            Properties properties,
            VehiclePaintDefinition paint,
            String subtitleKey,
            String canTexture,
            List<Component> details
    ) {
        super(properties);
        this.paint = paint;
        this.subtitleKey = subtitleKey;
        this.canTexture = canTexture;
        this.details = List.copyOf(details);
    }

    public VehiclePaintDefinition paint() {
        return paint;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        if (subtitleKey != null) {
            tooltip.add(Component.translatable(subtitleKey)
                    .withStyle(ChatFormatting.GRAY));
            if (!details.isEmpty() && VehicleTooltips.details(tooltip, flag)) tooltip.addAll(details);
            return;
        }
        VehiclePaintDefinitions.number(paint).ifPresent(number ->
                tooltip.add(Component.translatable("tooltip.polaris_vehicle.paint_series")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable(
                                "tooltip.polaris_vehicle.paint_number", number
                        ).withStyle(ChatFormatting.GOLD))));
    }

    @Override
    public String geckoAssetFolder() {
        return "item";
    }

    @Override
    public String geckoAssetName() {
        return ASSET_NAME;
    }

    @Override
    public String geckoTextureName() {
        return canTexture;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<VehiclePaintItem>install(consumer);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
