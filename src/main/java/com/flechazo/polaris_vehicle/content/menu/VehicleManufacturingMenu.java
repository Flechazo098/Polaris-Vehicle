package com.flechazo.polaris_vehicle.content.menu;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingCategory;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingDisplayOrder;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingMaterials;
import com.flechazo.polaris_vehicle.manufacturing.VehicleManufacturingRecipe;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.registry.ModMenus;
import com.flechazo.polaris_vehicle.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
public final class VehicleManufacturingMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 339;
    public static final int IMAGE_HEIGHT = 243;

    public static final int PLAYER_INVENTORY_FRAME_X = 28;
    public static final int PLAYER_INVENTORY_FRAME_Y = 150;
    public static final int PLAYER_HOTBAR_FRAME_X = 28;
    public static final int PLAYER_HOTBAR_FRAME_Y = 209;
    public static final int PLAYER_SLOT_OFFSET = 0;
    public static final int PLAYER_COLUMN_STEP = 18;
    public static final int PLAYER_ROW_STEP = 18;

    public static final int RESULT_FRAME_X = 265;
    public static final int RESULT_FRAME_Y = 200;
    public static final int RESULT_SLOT_X = RESULT_FRAME_X;
    public static final int RESULT_SLOT_Y = RESULT_FRAME_Y;

    public static final int RESULT_SLOT_INDEX = 0;
    public static final int PLAYER_MAIN_START = 1;
    public static final int PLAYER_HOTBAR_START = PLAYER_MAIN_START + 27;
    public static final int PLAYER_END = PLAYER_HOTBAR_START + 9;

    public static final int PREVIOUS_PAGE_BUTTON = 0;
    public static final int NEXT_PAGE_BUTTON = 1;
    public static final int RECIPE_BUTTON_BASE = 100;
    private static final int DATA_CATEGORY = 0;
    private static final int DATA_SELECTED_RECIPE = 1;
    private static final int DATA_COUNT = 2;

    private final Inventory playerInventory;
    private final BlockPos tablePos;
    private final Container resultContainer = new SimpleContainer(1);
    private final ContainerData data;

    public VehicleManufacturingMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf buffer
    ) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public VehicleManufacturingMenu(int containerId, Inventory inventory, BlockPos tablePos) {
        this(containerId, inventory, tablePos, new SimpleContainerData(DATA_COUNT));
    }

    VehicleManufacturingMenu(
            int containerId,
            Inventory inventory,
            BlockPos tablePos,
            ContainerData data
    ) {
        super(ModMenus.VEHICLE_MANUFACTURING_TABLE.get(), containerId);
        this.playerInventory = inventory;
        this.tablePos = tablePos.immutable();
        this.data = data;
        data.set(DATA_CATEGORY, VehicleManufacturingCategory.VEHICLE.ordinal());
        data.set(DATA_SELECTED_RECIPE, -1);

        addSlot(new ManufacturingResultSlot(
                this,
                resultContainer,
                RESULT_SLOT_INDEX,
                RESULT_SLOT_X,
                RESULT_SLOT_Y
        ));
        addPlayerInventory(inventory);
        addDataSlots(data);
        refreshResult();
    }

    public VehicleManufacturingCategory category() {
        int index = Math.clamp(
                data.get(DATA_CATEGORY),
                0,
                VehicleManufacturingCategory.values().length - 1
        );
        return VehicleManufacturingCategory.values()[index];
    }

    public int selectedRecipeIndex() {
        return data.get(DATA_SELECTED_RECIPE);
    }

    public List<RecipeHolder<VehicleManufacturingRecipe>> visibleRecipes() {
        VehicleManufacturingCategory category = category();
        return playerInventory.player.level()
                .getRecipeManager()
                .getAllRecipesFor(ModRecipes.VEHICLE_MANUFACTURING_TYPE.get())
                .stream()
                .filter(holder -> holder.value().category() == category)
                .filter(holder -> !holder.value().isIncomplete())
                .sorted(VehicleManufacturingDisplayOrder.forCategory(category))
                .toList();
    }

    public Maybe<RecipeHolder<VehicleManufacturingRecipe>> selectedRecipe() {
        List<RecipeHolder<VehicleManufacturingRecipe>> recipes = visibleRecipes();
        int index = selectedRecipeIndex();
        return index >= 0 && index < recipes.size()
                ? Maybe.some(recipes.get(index))
                : Maybe.none();
    }

    public VehicleManufacturingMaterials.Match materialMatch(VehicleManufacturingRecipe recipe) {
        return VehicleManufacturingMaterials.match(playerInventory.items, recipe.materials());
    }

    public boolean canCraftSelected() {
        return selectedRecipe()
                .map(RecipeHolder::value)
                .map(this::materialMatch)
                .fold(() -> false, VehicleManufacturingMaterials.Match::complete);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!stillValid(player)) {
            return false;
        }
        if (buttonId == PREVIOUS_PAGE_BUTTON || buttonId == NEXT_PAGE_BUTTON) {
            int direction = buttonId == PREVIOUS_PAGE_BUTTON ? -1 : 1;
            int next = Math.floorMod(
                    category().ordinal() + direction,
                    VehicleManufacturingCategory.values().length
            );
            data.set(DATA_CATEGORY, next);
            data.set(DATA_SELECTED_RECIPE, -1);
            refreshResult();
            broadcastChanges();
            return true;
        }
        int recipeIndex = buttonId - RECIPE_BUTTON_BASE;
        if (recipeIndex < 0 || recipeIndex >= visibleRecipes().size()) {
            return false;
        }
        data.set(DATA_SELECTED_RECIPE, recipeIndex);
        refreshResult();
        broadcastChanges();
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        refreshResult();
        super.slotsChanged(container);
    }

    @Override
    public void broadcastChanges() {
        refreshResult();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(player.level(), tablePos),
                player,
                ModBlocks.VEHICLE_MANUFACTURING_TABLE.get()
        );
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != RESULT_SLOT_INDEX && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= PLAYER_END) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT_INDEX) {
            return quickMoveResult(player);
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved = index < PLAYER_HOTBAR_START
                ? moveItemStackTo(source, PLAYER_HOTBAR_START, PLAYER_END, false)
                : moveItemStackTo(source, PLAYER_MAIN_START, PLAYER_HOTBAR_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            sourceSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return original;
    }

    private ItemStack quickMoveResult(Player player) {
        return selectedRecipe().fold(
                () -> ItemStack.EMPTY,
                holder -> {
                    VehicleManufacturingRecipe recipe = holder.value();
                    ItemStack crafted = recipe.getResultItem(player.registryAccess());
                    if (!materialMatch(recipe).complete() || !canFullyInsert(crafted)) {
                        return ItemStack.EMPTY;
                    }
                    if (!VehicleManufacturingMaterials.consume(playerInventory, recipe.materials())) {
                        return ItemStack.EMPTY;
                    }
                    ItemStack moving = crafted.copy();
                    if (!moveItemStackTo(moving, PLAYER_MAIN_START, PLAYER_END, true)
                            || !moving.isEmpty()) {
                        throw new IllegalStateException("Manufacturing result preflight and insertion disagreed");
                    }
                    crafted.onCraftedBy(player.level(), player, crafted.getCount());
                    refreshResult();
                    broadcastChanges();
                    return crafted;
                }
        );
    }

    private boolean consumeSelected(Player player) {
        if (player != playerInventory.player || !stillValid(player)) {
            return false;
        }
        return selectedRecipe().fold(
                () -> false,
                holder -> {
                    VehicleManufacturingRecipe recipe = holder.value();
                    if (!materialMatch(recipe).complete()) {
                        return false;
                    }
                    boolean consumed = VehicleManufacturingMaterials.consume(playerInventory, recipe.materials());
                    if (consumed) {
                        refreshResult();
                        broadcastChanges();
                    }
                    return consumed;
                }
        );
    }

    private boolean canFullyInsert(ItemStack result) {
        int remaining = result.getCount();
        for (int index = PLAYER_MAIN_START; index < PLAYER_END && remaining > 0; index++) {
            Slot slot = slots.get(index);
            if (!slot.mayPlace(result)) {
                continue;
            }
            ItemStack present = slot.getItem();
            if (present.isEmpty()) {
                remaining -= Math.min(slot.getMaxStackSize(result), result.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(present, result)) {
                remaining -= Math.max(0, slot.getMaxStackSize(result) - present.getCount());
            }
        }
        return remaining <= 0;
    }

    private void refreshResult() {
        if (playerInventory.player.level().isClientSide()) {
            return;
        }
        ItemStack preview = selectedRecipe().fold(
                () -> ItemStack.EMPTY,
                holder -> materialMatch(holder.value()).complete()
                        ? holder.value().getResultItem(playerInventory.player.registryAccess())
                        : ItemStack.EMPTY
        );
        ItemStack current = resultContainer.getItem(RESULT_SLOT_INDEX);
        if (current.getCount() == preview.getCount()
                && ItemStack.isSameItemSameComponents(current, preview)) {
            return;
        }
        resultContainer.setItem(RESULT_SLOT_INDEX, preview);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_FRAME_X + PLAYER_SLOT_OFFSET + column * PLAYER_COLUMN_STEP,
                        PLAYER_INVENTORY_FRAME_Y + PLAYER_SLOT_OFFSET + row * PLAYER_ROW_STEP
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    PLAYER_HOTBAR_FRAME_X + PLAYER_SLOT_OFFSET + column * PLAYER_COLUMN_STEP,
                    PLAYER_HOTBAR_FRAME_Y + PLAYER_SLOT_OFFSET
            ));
        }
    }

    private static final class ManufacturingResultSlot extends Slot {
        private final VehicleManufacturingMenu menu;

        ManufacturingResultSlot(
                VehicleManufacturingMenu menu,
                Container container,
                int slot,
                int x,
                int y
        ) {
            super(container, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return menu.canCraftSelected();
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (menu.consumeSelected(player)) {
                stack.onCraftedBy(player.level(), player, stack.getCount());
            }
            super.onTake(player, stack);
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }
}
