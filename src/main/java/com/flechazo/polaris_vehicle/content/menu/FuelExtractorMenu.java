package com.flechazo.polaris_vehicle.content.menu;

import com.flechazo.polaris_vehicle.content.block.entity.FuelExtractorBlockEntity;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class FuelExtractorMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 195;
    public static final int IMAGE_HEIGHT = 227;
    public static final int BLAZE_ROD_FRAME_X = 27;
    public static final int BLAZE_ROD_FRAME_Y = 26;
    public static final int BLAZE_ROD_SLOT_X = BLAZE_ROD_FRAME_X + 4;
    public static final int BLAZE_ROD_SLOT_Y = BLAZE_ROD_FRAME_Y + 4;
    public static final int COAL_FRAME_X = 71;
    public static final int COAL_FRAME_Y = 26;
    public static final int COAL_SLOT_X = COAL_FRAME_X + 4;
    public static final int COAL_SLOT_Y = COAL_FRAME_Y + 4;
    public static final int COMBUSTIBLE_FRAME_X = 47;
    public static final int COMBUSTIBLE_FRAME_Y = 81;
    public static final int COMBUSTIBLE_SLOT_X = COMBUSTIBLE_FRAME_X + 6;
    public static final int COMBUSTIBLE_SLOT_Y = COMBUSTIBLE_FRAME_Y + 6;
    public static final int PORTABLE_TANK_FRAME_X = 165;
    public static final int PORTABLE_TANK_FRAME_Y = 51;
    public static final int PORTABLE_TANK_SLOT_X = PORTABLE_TANK_FRAME_X + 4;
    public static final int PORTABLE_TANK_SLOT_Y = PORTABLE_TANK_FRAME_Y + 4;

    public static final int PLAYER_INVENTORY_FRAME_X = 17;
    public static final int PLAYER_INVENTORY_FRAME_Y = 141;
    public static final int PLAYER_INVENTORY_SLOT_X = PLAYER_INVENTORY_FRAME_X + 1;
    public static final int PLAYER_INVENTORY_SLOT_Y = PLAYER_INVENTORY_FRAME_Y + 1;
    public static final int PLAYER_HOTBAR_FRAME_X = 17;
    public static final int PLAYER_HOTBAR_FRAME_Y = 202;
    public static final int PLAYER_HOTBAR_SLOT_X = PLAYER_HOTBAR_FRAME_X + 1;
    public static final int PLAYER_HOTBAR_SLOT_Y = PLAYER_HOTBAR_FRAME_Y + 1;

    public static final int MACHINE_SLOT_COUNT = FuelExtractorBlockEntity.SLOT_COUNT;
    private static final int DATA_PROGRESS = 0;
    private static final int DATA_TOTAL_PROGRESS = 1;
    private static final int DATA_STORED_FUEL = 2;
    private static final int DATA_FUEL_CAPACITY = 3;
    private static final int DATA_BURN_TIME_REMAINING = 4;
    private static final int DATA_BURN_TIME_TOTAL = 5;
    public static final int DATA_COUNT = 6;

    private final BlockPos extractorPos;
    private final ContainerData data;

    public FuelExtractorMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf buffer
    ) {
        this(
                containerId,
                inventory,
                new SimpleContainer(MACHINE_SLOT_COUNT),
                new SimpleContainerData(DATA_COUNT),
                buffer.readBlockPos()
        );
    }

    public FuelExtractorMenu(
            int containerId,
            Inventory inventory,
            Container extractor,
            ContainerData data,
            BlockPos extractorPos
    ) {
        super(ModMenus.FUEL_EXTRACTOR.get(), containerId);
        checkContainerSize(extractor, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, DATA_COUNT);
        this.extractorPos = extractorPos.immutable();
        this.data = data;

        addSlot(new ValidatedSlot(
                extractor,
                FuelExtractorBlockEntity.BLAZE_ROD_SLOT,
                BLAZE_ROD_SLOT_X,
                BLAZE_ROD_SLOT_Y
        ));
        addSlot(new ValidatedSlot(
                extractor,
                FuelExtractorBlockEntity.COAL_SLOT,
                COAL_SLOT_X,
                COAL_SLOT_Y
        ));
        addSlot(new ValidatedSlot(
                extractor,
                FuelExtractorBlockEntity.COMBUSTIBLE_SLOT,
                COMBUSTIBLE_SLOT_X,
                COMBUSTIBLE_SLOT_Y
        ));
        addSlot(new ValidatedSlot(
                extractor,
                FuelExtractorBlockEntity.TANK_SLOT,
                PORTABLE_TANK_SLOT_X,
                PORTABLE_TANK_SLOT_Y
        ));
        addPlayerInventory(inventory);
        addDataSlots(data);
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int totalProgress() {
        return data.get(DATA_TOTAL_PROGRESS);
    }

    public int storedFuelMl() {
        return data.get(DATA_STORED_FUEL);
    }

    public int fuelCapacityMl() {
        return data.get(DATA_FUEL_CAPACITY);
    }

    public int burnTimeRemaining() {
        return data.get(DATA_BURN_TIME_REMAINING);
    }

    public int burnTimeTotal() {
        return data.get(DATA_BURN_TIME_TOTAL);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(
                        player.level(),
                        extractorPos
                ),
                player,
                ModBlocks.FUEL_EXTRACTOR.get()
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int playerInventoryStart = MACHINE_SLOT_COUNT;
        int playerHotbarStart = playerInventoryStart + 27;
        int playerInventoryEnd = playerHotbarStart + 9;
        if (index < 0 || index >= playerInventoryEnd) {
            return ItemStack.EMPTY;
        }

        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < MACHINE_SLOT_COUNT) {
            moved = moveItemStackTo(source, playerInventoryStart, playerInventoryEnd, true);
        } else if (slots.get(FuelExtractorBlockEntity.TANK_SLOT).mayPlace(source)) {
            moved = moveItemStackTo(
                    source,
                    FuelExtractorBlockEntity.TANK_SLOT,
                    FuelExtractorBlockEntity.TANK_SLOT + 1,
                    false
            );
        } else if (slots.get(FuelExtractorBlockEntity.BLAZE_ROD_SLOT).mayPlace(source)) {
            moved = moveItemStackTo(
                    source,
                    FuelExtractorBlockEntity.BLAZE_ROD_SLOT,
                    FuelExtractorBlockEntity.BLAZE_ROD_SLOT + 1,
                    false
            );
        } else if (slots.get(FuelExtractorBlockEntity.COAL_SLOT).mayPlace(source)) {
            moved = moveItemStackTo(
                    source,
                    FuelExtractorBlockEntity.COAL_SLOT,
                    FuelExtractorBlockEntity.COAL_SLOT + 1,
                    false
            );
        } else if (slots.get(FuelExtractorBlockEntity.COMBUSTIBLE_SLOT).mayPlace(source)) {
            moved = moveItemStackTo(
                    source,
                    FuelExtractorBlockEntity.COMBUSTIBLE_SLOT,
                    FuelExtractorBlockEntity.COMBUSTIBLE_SLOT + 1,
                    false
            );
        } else if (index < playerHotbarStart) {
            moved = moveItemStackTo(source, playerHotbarStart, playerInventoryEnd, false);
        } else {
            moved = moveItemStackTo(source, playerInventoryStart, playerHotbarStart, false);
        }
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

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_SLOT_X + column * 18,
                        PLAYER_INVENTORY_SLOT_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    PLAYER_HOTBAR_SLOT_X + column * 18,
                    PLAYER_HOTBAR_SLOT_Y
            ));
        }
    }

    private static final class ValidatedSlot extends Slot {
        ValidatedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
