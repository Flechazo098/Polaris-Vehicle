package com.flechazo.polaris_vehicle.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
public record FuelBufferData(ResourceLocation fuelType, int amountMl) {
    public static final Codec<FuelBufferData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fuel_type").forGetter(FuelBufferData::fuelType),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("amount_ml").forGetter(FuelBufferData::amountMl)
    ).apply(instance, FuelBufferData::new));

    public static FuelBufferData empty() {
        return new FuelBufferData(VehicleFuelTypes.STANDARD, 0);
    }

    public boolean canAccept(ResourceLocation incomingFuelType) {
        return amountMl == 0 || fuelType.equals(incomingFuelType);
    }

    public FuelBufferData withContents(
            ResourceLocation newFuelType,
            int newAmountMl,
            int capacityMl
    ) {
        if (newAmountMl <= 0) {
            return new FuelBufferData(newFuelType, 0);
        }
        if (!canAccept(newFuelType)) {
            throw new IllegalArgumentException(
                    "Cannot mix " + newFuelType + " into fuel buffer containing " + fuelType
            );
        }
        return new FuelBufferData(newFuelType, Math.clamp(newAmountMl, 0, capacityMl));
    }

    public FuelBufferData sanitized(int capacityMl) {
        return new FuelBufferData(fuelType, Math.clamp(amountMl, 0, capacityMl));
    }
}
