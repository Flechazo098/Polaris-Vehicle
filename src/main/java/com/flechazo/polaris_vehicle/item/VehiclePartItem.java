package com.flechazo.polaris_vehicle.item;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTireParameters;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
public class VehiclePartItem extends Item {
    private static final String DURABILITY = "PolarisDurability";
    private static final String MAX_DURABILITY = "PolarisMaxDurability";
    private final ResourceLocation partId;
    private final VehiclePartCategory category;
    private final int maxDurability;

    public VehiclePartItem(Properties properties, ResourceLocation partId,
                           VehiclePartCategory category, int maxDurability) {
        super(properties);
        this.partId = partId;
        this.category = category;
        this.maxDurability = maxDurability;
    }

    public ResourceLocation partId() {
        return partId;
    }

    public VehiclePartCategory category() {
        return category;
    }

    public int maxDurability() {
        return category == VehiclePartCategory.WHEEL
                ? VehicleTireParameters.resolve(partId, maxDurability).maxDurability()
                : maxDurability;
    }

    public InstalledPart readPart(ItemStack stack) {
        int maximum = maxDurability();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int durability = Math.clamp(tag.getInt(DURABILITY), 0, maximum);
        if (!tag.contains(DURABILITY)) durability = maximum;
        if (tag.contains(DURABILITY) && tag.contains(MAX_DURABILITY) && tag.getInt(MAX_DURABILITY) > 0) {
            int storedMaximum = tag.getInt(MAX_DURABILITY);
            var stored = new InstalledPart(partId, category,
                    Math.clamp(tag.getInt(DURABILITY), 0, storedMaximum), storedMaximum, 0, true);
            if (category == VehiclePartCategory.WHEEL) {
                return VehicleTireDefinitions.applyDurabilityParameters(stored);
            }
        }
        return InstalledPart.create(partId, category, maximum).withDurability(durability);
    }

    public void writePart(ItemStack stack, InstalledPart part) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(DURABILITY, part.durability());
        tag.putInt(MAX_DURABILITY, part.maxDurability());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack stackFor(InstalledPart part) {
        InstalledPart current = VehicleTireDefinitions.applyDurabilityParameters(part);
        return Pathway.just(BuiltInRegistries.ITEM.get(current.partId()))
                .filter(VehiclePartItem.class::isInstance)
                .map(VehiclePartItem.class::cast)
                .filter(item -> item.category() == current.category()
                        && item.maxDurability() == current.maxDurability())
                .map(item -> {
                    ItemStack stack = new ItemStack(item);
                    item.writePart(stack, current);
                    return stack;
                })
                .getOrElse(ItemStack.EMPTY);
    }

    public boolean repair(ItemStack stack, int amount) {
        InstalledPart current = readPart(stack);
        InstalledPart repaired = current.repair(amount);
        if (repaired == current) {
            return false;
        }
        writePart(stack, repaired);
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        InstalledPart part = readPart(stack);
        tooltip.add(Component.translatable(
                part.isScrapped()
                        ? "tooltip.polaris_vehicle.part_durability_broken"
                        : "tooltip.polaris_vehicle.part_durability",
                part.durability(),
                part.maxDurability()
        ).withStyle(part.isScrapped() ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (part.isScrapped()) {
            tooltip.add(Component.translatable("tooltip.polaris_vehicle.part_scrapped")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return readPart(stack).durability() < maxDurability();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        InstalledPart part = readPart(stack);
        return Math.round(13.0F * part.durability() / part.maxDurability());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        InstalledPart part = readPart(stack);
        float ratio = (float) part.durability() / part.maxDurability();
        return Mth.hsvToRgb(Math.max(0.0F, ratio) / 3.0F, 1.0F, 1.0F);
    }
}
