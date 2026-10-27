package com.flechazo.polaris_vehicle.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record PortableFuelTankData(ResourceLocation fuelType, int amountMl) {
    public static final Codec<PortableFuelTankData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fuel_type").forGetter(PortableFuelTankData::fuelType),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("amount_ml").forGetter(PortableFuelTankData::amountMl)
    ).apply(instance, PortableFuelTankData::new));

    public static PortableFuelTankData empty() {
        return new PortableFuelTankData(VehicleFuelTypes.STANDARD, 0);
    }

    public PortableFuelTankData withAmount(int newAmountMl, int capacityMl) {
        return new PortableFuelTankData(fuelType, Math.clamp(newAmountMl, 0, capacityMl));
    }

    public PortableFuelTankData sanitized(int capacityMl) {
        return withAmount(amountMl, capacityMl);
    }
}
