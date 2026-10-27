package com.flechazo.polaris_vehicle.content.menu;

import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.item.VehicleEngineItem;
import com.flechazo.polaris_vehicle.item.VehiclePowerUnitItem;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

final class AssemblyPartItemHandler implements IItemHandler {
    private final AssemblyTableBlockEntity table;
    private final Player player;
    private final List<VehiclePartSlot> slots;

    AssemblyPartItemHandler(
            AssemblyTableBlockEntity table,
            Player player,
            List<VehiclePartSlot> slots
    ) {
        this.table = table;
        this.player = player;
        this.slots = List.copyOf(slots);
    }

    @Override
    public int getSlots() {
        return slots.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return table.partStack(partSlot(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        validateSlot(slot);
        return table.insertPart(player, slots.get(slot), stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlot(slot);
        return table.extractPart(player, slots.get(slot), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlot(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        VehiclePartSlot target = partSlot(slot);
        if (target == VehiclePartSlot.ENGINE) {
            return stack.getItem() instanceof VehicleEngineItem;
        }
        if (!(stack.getItem() instanceof VehiclePowerUnitItem powerItem)) {
            return false;
        }
        return target == (powerItem.definition().isExtraPowerUnit()
                ? VehiclePartSlot.EXTRA_POWER_UNIT
                : VehiclePartSlot.ENERGY_RECOVERY_UNIT);
    }

    private VehiclePartSlot partSlot(int slot) {
        validateSlot(slot);
        return slots.get(slot);
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= slots.size()) {
            throw new IndexOutOfBoundsException(
                    "Assembly slot " + slot + " outside [0," + slots.size() + ")");
        }
    }
}
