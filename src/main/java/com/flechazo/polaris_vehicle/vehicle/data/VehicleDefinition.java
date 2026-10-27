package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

public record VehicleDefinition(
        ResourceLocation id,
        double bodyWeight,
        int fuelCapacity,
        Set<VehiclePartSlot> supportedSlots,
        Set<VehiclePartCategory> supportedPartCategories
) {
    private static final List<VehiclePartSlot> WHEEL_SLOT_ORDER = List.of(
            VehiclePartSlot.FRONT_LEFT_WHEEL,
            VehiclePartSlot.FRONT_RIGHT_WHEEL,
            VehiclePartSlot.FRONT_CENTER_WHEEL,
            VehiclePartSlot.REAR_LEFT_WHEEL,
            VehiclePartSlot.REAR_RIGHT_WHEEL
    );
    private static final Codec<Set<VehiclePartSlot>> SLOT_SET_CODEC = VehiclePartSlot.CODEC.listOf().xmap(
            Set::copyOf,
            List::copyOf
    );
    private static final Codec<Set<VehiclePartCategory>> CATEGORY_SET_CODEC = VehiclePartCategory.CODEC.listOf().xmap(
            Set::copyOf,
            List::copyOf
    );

    public static final Codec<VehicleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(VehicleDefinition::id),
            Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("body_weight").forGetter(VehicleDefinition::bodyWeight),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("fuel_capacity").forGetter(VehicleDefinition::fuelCapacity),
            SLOT_SET_CODEC.fieldOf("supported_slots").forGetter(VehicleDefinition::supportedSlots),
            CATEGORY_SET_CODEC.fieldOf("supported_part_categories")
                    .forGetter(VehicleDefinition::supportedPartCategories)
    ).apply(instance, VehicleDefinition::new));

    public boolean supports(VehiclePartSlot slot, VehiclePartCategory category) {
        return supportedSlots.contains(slot)
                && supportedPartCategories.contains(category)
                && slot.accepts(category);
    }

    public List<VehiclePartSlot> wheelSlots() {
        return WHEEL_SLOT_ORDER.stream()
                .filter(supportedSlots::contains)
                .toList();
    }
}
