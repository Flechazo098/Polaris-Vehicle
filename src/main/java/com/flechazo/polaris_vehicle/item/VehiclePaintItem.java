package com.flechazo.polaris_vehicle.item;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.client.renderer.StaticGeoItemRenderer;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class VehiclePaintItem extends Item implements GeoItem, StaticGeoAsset {
    private Set<ResourceLocation> vehicles;
    private Set<ResourceLocation> models;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehiclePaintItem(Properties properties) {
        super(properties);
    }

    private ResourceLocation name() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    private synchronized void loadResources() {
        if (vehicles != null) return;
        var supported = VehicleDefinitions.ALL.keySet().stream()
                .filter(vehicle -> hasResource(texture(vehicle))).collect(Collectors.toUnmodifiableSet());
        models = supported.stream().flatMap(vehicle -> Stream.of(
                        modelLocation(vehicle, "chassis"), modelLocation(vehicle, "control")))
                .filter(this::hasResource).collect(Collectors.toUnmodifiableSet());
        vehicles = supported;
    }

    public ResourceLocation paintId() {
        var name = name();
        return name.withPath(name.getPath().replaceFirst("_vehicle_paint$", ""));
    }

    public static Maybe<VehiclePaintItem> find(ResourceLocation paintId) {
        if (paintId == null) return Maybe.none();
        var item = BuiltInRegistries.ITEM.get(paintId.withPath(paintId.getPath() + "_vehicle_paint"));
        return item instanceof VehiclePaintItem paint ? Maybe.some(paint) : Maybe.none();
    }

    public boolean supports(ResourceLocation vehicle) {
        loadResources();
        return vehicles.contains(vehicle);
    }

    public ResourceLocation texture(ResourceLocation vehicle) {
        var name = name();
        return name.withPath("textures/entity/vehicle/" + vehicle.getPath() + "/" + name.getPath() + ".png");
    }

    public Maybe<ResourceLocation> model(ResourceLocation vehicle, String layer) {
        loadResources();
        var resource = modelLocation(vehicle, layer);
        return models.contains(resource) ? Maybe.some(resource) : Maybe.none();
    }

    private ResourceLocation modelLocation(ResourceLocation vehicle, String layer) {
        var name = name();
        return name.withPath("geo/vehicle/" + vehicle.getPath() + "/" + name.getPath() + "_" + layer + ".geo.json");
    }

    private boolean hasResource(ResourceLocation resource) {
        var file = ModList.get().getModFileById(resource.getNamespace());
        return file != null && Files.isRegularFile(file.getFile().findResource("assets", resource.getNamespace(), resource.getPath()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var name = name();
        String text = Component.translatable("tooltip." + name.getNamespace() + "." + name.getPath()).getString();
        for (String line : text.split("\\n")) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public String geckoAssetFolder() {
        return "item";
    }

    @Override
    public String geckoAssetName() {
        return name().getPath();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new StaticGeoItemRenderer<VehiclePaintItem>(new DefaultedItemGeoModel<>(name()));
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
}
