package com.flechazo.polaris_vehicle.fuel;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record FuelExtractorRecipeInput(
        ItemStack blazeRods,
        ItemStack coal
) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return switch (index) {
            case 0 -> blazeRods;
            case 1 -> coal;
            default -> throw new IllegalArgumentException("No fuel extractor input at index " + index);
        };
    }

    @Override
    public int size() {
        return 2;
    }
}
