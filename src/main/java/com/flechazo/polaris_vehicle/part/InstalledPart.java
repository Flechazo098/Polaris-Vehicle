package com.flechazo.polaris_vehicle.part;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
public record InstalledPart(
        ResourceLocation partId,
        VehiclePartCategory category,
        int durability,
        int maxDurability,
        int tightening,
        boolean enabled
) {
    public static final Codec<InstalledPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("part_id").forGetter(InstalledPart::partId),
            VehiclePartCategory.CODEC.fieldOf("category").forGetter(InstalledPart::category),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("durability").forGetter(InstalledPart::durability),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_durability").forGetter(InstalledPart::maxDurability),
            Codec.intRange(0, 4).fieldOf("tightening").forGetter(InstalledPart::tightening),
            Codec.BOOL.fieldOf("enabled").forGetter(InstalledPart::enabled)
    ).apply(instance, InstalledPart::new));

    public static InstalledPart create(
            ResourceLocation partId,
            VehiclePartCategory category,
            int maxDurability
    ) {
        return new InstalledPart(partId, category, maxDurability, maxDurability, 0, true);
    }

    public boolean isScrapped() {
        return durability == 0;
    }

    public boolean isFullyTightened() {
        return category != VehiclePartCategory.WHEEL || tightening == 4;
    }

    public InstalledPart withDurability(int newDurability) {
        int clamped = Math.clamp(newDurability, 0, maxDurability);
        return new InstalledPart(partId, category, clamped, maxDurability, tightening, enabled);
    }

    public InstalledPart repair(int amount) {
        if (amount <= 0 || isScrapped() || durability >= maxDurability) {
            return this;
        }
        int repaired = durability + Math.min(amount, maxDurability - durability);
        return withDurability(repaired);
    }

    public InstalledPart withTightening(int newTightening) {
        if (category != VehiclePartCategory.WHEEL) {
            return this;
        }
        int clamped = Math.clamp(newTightening, 0, 4);
        return new InstalledPart(partId, category, durability, maxDurability, clamped, enabled);
    }

    public InstalledPart withEnabled(boolean newEnabled) {
        return new InstalledPart(partId, category, durability, maxDurability, tightening, newEnabled);
    }
}
