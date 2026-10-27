package com.flechazo.polaris_vehicle.manufacturing;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.registry.ModRecipes;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;
public record VehicleManufacturingRecipe(
        VehicleManufacturingCategory category,
        List<SizedIngredient> materials,
        ItemStack result
) implements Recipe<VehicleManufacturingRecipeInput> {
    public static final int MAX_MATERIALS = 3;
    public static final MapCodec<VehicleManufacturingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    CodecUtil.enumCodec(VehicleManufacturingCategory.class)
                            .fieldOf("category")
                            .forGetter(VehicleManufacturingRecipe::category),
                    SizedIngredient.FLAT_CODEC.listOf(1, MAX_MATERIALS).fieldOf("materials")
                            .forGetter(VehicleManufacturingRecipe::materials),
                    ItemStack.STRICT_CODEC.fieldOf("result")
                            .forGetter(VehicleManufacturingRecipe::result)
            ).apply(instance, VehicleManufacturingRecipe::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleManufacturingRecipe> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VehicleManufacturingRecipe decode(RegistryFriendlyByteBuf buffer) {
                    VehicleManufacturingCategory category = buffer.readEnum(VehicleManufacturingCategory.class);
                    int size = buffer.readVarInt();
                    if (size < 1 || size > MAX_MATERIALS) {
                        throw new IllegalArgumentException("Manufacturing material count out of range: " + size);
                    }
                    List<SizedIngredient> materials = Pathway.listPathRange(0, size)
                            .map(ignored -> SizedIngredient.STREAM_CODEC.decode(buffer))
                            .run();
                    return new VehicleManufacturingRecipe(
                            category,
                            materials,
                            ItemStack.STREAM_CODEC.decode(buffer)
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, VehicleManufacturingRecipe recipe) {
                    buffer.writeEnum(recipe.category());
                    buffer.writeVarInt(recipe.materials().size());
                    recipe.materials().forEach(material ->
                            SizedIngredient.STREAM_CODEC.encode(buffer, material));
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result());
                }
            };

    @Override
    public boolean matches(VehicleManufacturingRecipeInput input, Level level) {
        return VehicleManufacturingMaterials.match(input.items(), materials).complete();
    }

    @Override
    public ItemStack assemble(VehicleManufacturingRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        Pathway.listPath(materials).map(SizedIngredient::ingredient).peek(ingredients::add);
        return ingredients;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.VEHICLE_MANUFACTURING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.VEHICLE_MANUFACTURING_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<VehicleManufacturingRecipe> {
        @Override
        public MapCodec<VehicleManufacturingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, VehicleManufacturingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
