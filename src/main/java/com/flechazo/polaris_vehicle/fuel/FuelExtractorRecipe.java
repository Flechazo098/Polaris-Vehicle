package com.flechazo.polaris_vehicle.fuel;

import com.flechazo.polaris_vehicle.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Objects;

public record FuelExtractorRecipe(
        Ingredient blazeRodIngredient,
        int blazeRodCount,
        Ingredient coalIngredient,
        int coalCount,
        ResourceLocation fuelType,
        int outputAmountMl,
        int processingTicks
) implements Recipe<FuelExtractorRecipeInput> {
    public static final MapCodec<FuelExtractorRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("blaze_rod").forGetter(FuelExtractorRecipe::blazeRodIngredient),
            Codec.intRange(1, 64).fieldOf("blaze_rod_count")
                    .forGetter(FuelExtractorRecipe::blazeRodCount),
            Ingredient.CODEC_NONEMPTY.fieldOf("coal").forGetter(FuelExtractorRecipe::coalIngredient),
            Codec.intRange(1, 64).fieldOf("coal_count")
                    .forGetter(FuelExtractorRecipe::coalCount),
            ResourceLocation.CODEC.fieldOf("fuel_type").forGetter(FuelExtractorRecipe::fuelType),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("output_amount_ml")
                    .forGetter(FuelExtractorRecipe::outputAmountMl),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("processing_ticks")
                    .forGetter(FuelExtractorRecipe::processingTicks)
    ).apply(instance, FuelExtractorRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuelExtractorRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FuelExtractorRecipe decode(RegistryFriendlyByteBuf buffer) {
            return new FuelExtractorRecipe(
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FuelExtractorRecipe recipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.blazeRodIngredient());
            buffer.writeVarInt(recipe.blazeRodCount());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.coalIngredient());
            buffer.writeVarInt(recipe.coalCount());
            ResourceLocation.STREAM_CODEC.encode(buffer, recipe.fuelType());
            buffer.writeVarInt(recipe.outputAmountMl());
            buffer.writeVarInt(recipe.processingTicks());
        }
    };

    public FuelExtractorRecipe {
        Objects.requireNonNull(blazeRodIngredient, "blazeRodIngredient");
        Objects.requireNonNull(coalIngredient, "coalIngredient");
        Objects.requireNonNull(fuelType, "fuelType");
        if (blazeRodCount < 1 || blazeRodCount > 64
                || coalCount < 1 || coalCount > 64
                || outputAmountMl < 1
                || processingTicks < 1) {
            throw new IllegalArgumentException("Fuel extractor recipe values are outside their supported range");
        }
        if (!VehicleFuelTypes.isAccepted(fuelType)) {
            throw new IllegalArgumentException("Unsupported extractor fuel type: " + fuelType);
        }
    }

    @Override
    public boolean matches(FuelExtractorRecipeInput input, Level level) {
        return input.blazeRods().getCount() >= blazeRodCount
                && blazeRodIngredient.test(input.blazeRods())
                && input.coal().getCount() >= coalCount
                && coalIngredient.test(input.coal());
    }

    public boolean acceptsBlazeRods(ItemStack stack) {
        return blazeRodIngredient.test(stack);
    }

    public boolean acceptsCoal(ItemStack stack) {
        return coalIngredient.test(stack);
    }

    @Override
    public ItemStack assemble(FuelExtractorRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(blazeRodIngredient);
        ingredients.add(coalIngredient);
        return ingredients;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FUEL_EXTRACTION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.FUEL_EXTRACTION_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<FuelExtractorRecipe> {
        @Override
        public MapCodec<FuelExtractorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FuelExtractorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
