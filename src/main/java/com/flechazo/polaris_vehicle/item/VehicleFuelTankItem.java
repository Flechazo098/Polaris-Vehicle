package com.flechazo.polaris_vehicle.item;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.fuel.PortableFuelTankData;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public final class VehicleFuelTankItem extends Item implements GeoItem, StaticGeoAsset {
    private static final String DATA_KEY = "PolarisFuelTank";

    private final int capacityMl;
    private final int transferPerIntervalMl;
    private final String modelAssetName;
    private final String textureAssetName;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public VehicleFuelTankItem(
            Properties properties,
            int capacityMl,
            int transferPerIntervalMl,
            String modelAssetName,
            String textureAssetName
    ) {
        super(properties);
        if (capacityMl <= 0 || transferPerIntervalMl <= 0) {
            throw new IllegalArgumentException("Fuel tank capacity and transfer rate must be positive");
        }
        if (modelAssetName == null || modelAssetName.isBlank()
                || textureAssetName == null || textureAssetName.isBlank()) {
            throw new IllegalArgumentException("Fuel tank model and texture asset names must not be blank");
        }
        this.capacityMl = capacityMl;
        this.transferPerIntervalMl = transferPerIntervalMl;
        this.modelAssetName = modelAssetName;
        this.textureAssetName = textureAssetName;
    }

    public int capacityMl() {
        return capacityMl;
    }

    public int transferPerIntervalMl() {
        return transferPerIntervalMl;
    }

    @Override
    public String geckoAssetFolder() {
        return "item";
    }

    @Override
    public String geckoAssetName() {
        return modelAssetName;
    }

    @Override
    public String geckoTextureName() {
        return textureAssetName;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        StaticGeoProvider.<VehicleFuelTankItem>install(consumer);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    public PortableFuelTankData readData(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(DATA_KEY)) {
            return PortableFuelTankData.empty();
        }
        return Pathway.either(CodecUtil.toEither(
                        PortableFuelTankData.CODEC.parse(NbtOps.INSTANCE, root.get(DATA_KEY))))
                .peekLeft(error -> PolarisVehicle.LOGGER.warn(
                        "Invalid portable fuel tank data on {}: {}",
                        stack.getItem(),
                        error
                ))
                .map(data -> data.sanitized(capacityMl))
                .getOrElse(PortableFuelTankData.empty());
    }

    public void writeData(ItemStack stack, PortableFuelTankData data) {
        PortableFuelTankData sanitized = data.sanitized(capacityMl);
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var encoded = PortableFuelTankData.CODEC.encodeStart(NbtOps.INSTANCE, sanitized).getOrThrow();
        root.put(DATA_KEY, encoded);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        PortableFuelTankData data = readData(stack);
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.fuel_tank_amount",
                data.amountMl(),
                capacityMl
        ).withStyle(ChatFormatting.GRAY));
        if (!VehicleTooltips.details(tooltip, flag)) return;
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.fuel_tank_rate",
                transferPerIntervalMl
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72_000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return readData(stack).amountMl() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * readData(stack).amountMl() / capacityMl);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) readData(stack).amountMl() / capacityMl;
        return Mth.hsvToRgb(0.55F, 0.85F, Math.clamp(0.45F + ratio * 0.55F, 0.0F, 1.0F));
    }
}
