package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.client.renderer.VehicleTireItemRenderer;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinition;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.physics.WheelTerrain;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

public final class VehicleTireItem extends VehiclePartItem implements GeoItem, StaticGeoAsset {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehicleTireItem(Properties properties, ResourceLocation partId, int maxDurability) {
        super(properties, partId, VehiclePartCategory.WHEEL, maxDurability);
    }

    @Override
    public String geckoAssetFolder() {
        return "vehicle/tires/formula_car";
    }

    @Override
    public String geckoAssetName() {
        return switch (partId().getPath()) {
            case "racing_speed_tire", "racing_balanced_tire", "racing_endurance_tire" -> "racing_tire";
            default -> partId().getPath();
        };
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new VehicleTireItemRenderer();
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
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        VehicleTireDefinitions.find(partId()).ifPresent(definition -> {
            if (!VehicleTooltips.details(tooltip, flag)) return;
            var part = readPart(stack);
            tooltip.add(VehicleTooltips.heading(Component.translatable(
                    "tooltip.polaris_vehicle.tire.terrain_header")));
            appendTerrainLine(tooltip, definition, part, WheelTerrain.NORMAL, "normal");
            appendTerrainLine(tooltip, definition, part, WheelTerrain.ROAD, "road");
            appendTerrainLine(tooltip, definition, part, WheelTerrain.SOFT, "soft");
            tooltip.add(VehicleTooltips.item(Component.translatable(
                    "tooltip.polaris_vehicle.tire.road_blocks"), ChatFormatting.DARK_GRAY));
            tooltip.add(VehicleTooltips.item(Component.translatable(
                    "tooltip.polaris_vehicle.tire.soft_blocks"), ChatFormatting.DARK_GRAY));
            if (definition.id().equals(VehicleTireDefinitions.RACING_SPEED.id())
                    || definition.id().equals(VehicleTireDefinitions.RACING_BALANCED.id())
                    || definition.id().equals(VehicleTireDefinitions.RACING_ENDURANCE.id())) {
                tooltip.add(Component.translatable(
                        "tooltip.polaris_vehicle.tire.racing_grass_penalty"
                ).withStyle(ChatFormatting.RED));
                tooltip.add(Component.translatable(
                        "tooltip.polaris_vehicle.tire.racing_sand_penalty"
                ).withStyle(ChatFormatting.RED));
                tooltip.add(Component.translatable(
                        "tooltip.polaris_vehicle.tire.racing_gravel_penalty"
                ).withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void appendTerrainLine(
            List<Component> tooltip,
            VehicleTireDefinition definition,
            InstalledPart part,
            WheelTerrain terrain,
            String terrainName
    ) {
        tooltip.add(VehicleTooltips.item(Component.translatable(
                "tooltip.polaris_vehicle.tire.terrain_line",
                Component.translatable("tooltip.polaris_vehicle.tire.terrain." + terrainName),
                signed(definition.speedModifier(part, terrain)),
                signed(definition.accelerationModifier(part, terrain))
        ), ChatFormatting.GRAY));
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }
}
