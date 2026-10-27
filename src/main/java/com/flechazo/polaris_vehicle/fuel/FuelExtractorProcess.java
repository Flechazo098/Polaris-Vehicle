package com.flechazo.polaris_vehicle.fuel;

public final class FuelExtractorProcess {

    public static StepResult tick(
            int progress,
            int storedFuelMl,
            int capacityMl,
            FuelExtractorRecipe recipe,
            boolean inputMatches
    ) {
        if (!inputMatches || storedFuelMl >= capacityMl) {
            return new StepResult(0, storedFuelMl, false);
        }
        if (progress < 0 || progress >= recipe.processingTicks()) {
            return new StepResult(0, storedFuelMl, false);
        }
        int nextProgress = progress + 1;
        if (nextProgress < recipe.processingTicks()) {
            return new StepResult(nextProgress, storedFuelMl, false);
        }
        int accepted = Math.min(capacityMl - storedFuelMl, recipe.outputAmountMl());
        return new StepResult(0, storedFuelMl + accepted, true, recipe.outputAmountMl() - accepted);
    }

    public record StepResult(int progress, int storedFuelMl, boolean consumeIngredients, int pendingOutputMl) {
        public StepResult(int progress, int storedFuelMl, boolean consumeIngredients) {
            this(progress, storedFuelMl, consumeIngredients, 0);
        }
    }
}
