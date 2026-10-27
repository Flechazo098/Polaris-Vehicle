package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.VehicleItem;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingCategory;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingRecipe;
import com.flechazo.polaris_vehicle.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

public final class VehicleDismantleReturns {
    public static List<ItemStack> forVehicle(Level level, ResourceLocation vehicleType) {
        var recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.VEHICLE_MANUFACTURING_TYPE.get());

        return Pathway.maybe(findRecipe(level, vehicleType, recipes))
                .map(holder -> recipeReturns(holder, vehicleType))
                .getOrElseGet(() -> {
                    PolarisVehicle.LOGGER.warn("[vehicle-dismantle] stage=recipe_failed type={} reason=no_matching_vehicle_recipe"
                            + " manufacturingRecipeCount={}", vehicleType, recipes.size());
                    return List.of();
                });
    }

    private static Maybe<RecipeHolder<VehicleManufacturingRecipe>> findRecipe(
            Level level, ResourceLocation vehicleType, List<RecipeHolder<VehicleManufacturingRecipe>> recipes
    ) {
        for (var holder : recipes) {
            if (holder.value().category() == VehicleManufacturingCategory.VEHICLE
                    && holder.value().getResultItem(level.registryAccess()).getItem()
                    instanceof VehicleItem vehicleItem && vehicleItem.definition().id().equals(vehicleType)) {
                return Maybe.some(holder);
            }
        }
        return Maybe.none();
    }

    private static List<ItemStack> recipeReturns(
            RecipeHolder<VehicleManufacturingRecipe> holder,
            ResourceLocation vehicleType
    ) {
        var recipe = holder.value();
        List<ItemStack> returns = new ArrayList<>(recipe.materials().size());

        int materialIndex = 0;
        for (SizedIngredient material : recipe.materials()) {
            ItemStack[] candidates = material.getItems();
            if (candidates.length == 0) {
                PolarisVehicle.LOGGER.warn("[vehicle-dismantle] stage=recipe_failed type={} recipe={}"
                                + " reason=empty_ingredient materialIndex={} requiredCount={} ingredient={}",
                        vehicleType, holder.id(), materialIndex, material.count(), material.ingredient());
                return List.of();
            }
            ItemStack stack = candidates[0].copy();
            stack.setCount(material.count());
            returns.add(stack);
            materialIndex++;
        }
        return List.copyOf(returns);
    }
}
