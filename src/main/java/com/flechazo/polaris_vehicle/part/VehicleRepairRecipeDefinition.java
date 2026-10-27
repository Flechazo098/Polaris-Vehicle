package com.flechazo.polaris_vehicle.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record VehicleRepairRecipeDefinition(
        ResourceLocation id,
        ResourceLocation materialTag,
        int materialCount,
        int repairAmount,
        int processingTicks,
        Set<VehiclePartCategory> categories
) {
    private static final Codec<Set<VehiclePartCategory>> CATEGORY_SET_CODEC =
            VehiclePartCategory.CODEC.listOf().xmap(Set::copyOf, List::copyOf);

    public static final Codec<VehicleRepairRecipeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VehicleRepairRecipeDefinition::id),
            ResourceLocation.CODEC.fieldOf("material_tag").forGetter(VehicleRepairRecipeDefinition::materialTag),
            Codec.intRange(1, 64).fieldOf("material_count")
                    .forGetter(VehicleRepairRecipeDefinition::materialCount),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("repair_amount")
                    .forGetter(VehicleRepairRecipeDefinition::repairAmount),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("processing_ticks")
                    .forGetter(VehicleRepairRecipeDefinition::processingTicks),
            CATEGORY_SET_CODEC.fieldOf("categories").forGetter(VehicleRepairRecipeDefinition::categories)
    ).apply(instance, VehicleRepairRecipeDefinition::new));

    public VehicleRepairRecipeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(materialTag, "materialTag");
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("Repair recipe must support at least one part category");
        }
    }

    public boolean matches(InstalledPart part, ItemStack material) {
        return !part.isScrapped()
                && part.durability() < part.maxDurability()
                && categories.contains(part.category())
                && material.getCount() >= materialCount
                && material.is(TagKey.create(Registries.ITEM, materialTag));
    }
}
