package com.flechazo.polaris_vehicle.content.menu;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.item.VehicleEngineItem;
import com.flechazo.polaris_vehicle.item.VehiclePartItem;
import com.flechazo.polaris_vehicle.item.VehiclePowerUnitItem;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.registry.ModMenus;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.List;
import java.util.function.Function;

public final class AssemblyTableMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 339;
    public static final int IMAGE_HEIGHT = 243;
    public static final int PLAYER_INVENTORY_FRAME_X = 28;
    public static final int PLAYER_INVENTORY_FRAME_Y = 150;
    public static final int PLAYER_HOTBAR_FRAME_X = 28;
    public static final int PLAYER_HOTBAR_FRAME_Y = 209;
    public static final int PLAYER_SLOT_OFFSET = 0;
    public static final int PLAYER_COLUMN_STEP = 18;
    public static final int PLAYER_ROW_STEP = 18;
    private static final int INSTALL_ACTION = 0;
    private static final int REMOVE_TO_CURSOR_ACTION = 1;
    private static final int QUICK_REMOVE_ACTION = 2;
    private static final int ACTIONS_PER_SLOT = 3;
    private static final List<VehiclePartSlot> MANAGED_SLOT_ORDER = List.of(
            VehiclePartSlot.ENGINE,
            VehiclePartSlot.ENERGY_RECOVERY_UNIT,
            VehiclePartSlot.EXTRA_POWER_UNIT
    );

    private final BlockPos tablePos;
    private final VehicleDefinition vehicleDefinition;
    private final List<VehiclePartSlot> managedSlots;
    private final SimpleContainer partMirrors;
    private final Player menuPlayer;
    private final VehicleInstanceData openedVehicleData;

    public AssemblyTableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(
                containerId,
                inventory,
                data.readBlockPos(),
                requireDefinition(data.readResourceLocation()),
                VehicleInstanceData.STREAM_CODEC.decode(data)
        );
    }

    public AssemblyTableMenu(
            int containerId,
            Inventory inventory,
            BlockPos tablePos,
            VehicleDefinition vehicleDefinition,
            VehicleInstanceData vehicleData
    ) {
        super(ModMenus.ASSEMBLY_TABLE.get(), containerId);
        this.tablePos = tablePos.immutable();
        this.vehicleDefinition = vehicleDefinition;
        this.openedVehicleData = vehicleData.sanitized(vehicleDefinition);
        this.managedSlots = managedSlotsFor(vehicleDefinition);
        this.partMirrors = new SimpleContainer(managedSlots.size());
        this.menuPlayer = inventory.player;

        refreshPartMirrors(inventory.player);
        for (int index = 0; index < managedSlots.size(); index++) {
            addSlot(new ReadOnlyPartSlot(
                    partMirrors,
                    index,
                    uiSlot(managedSlots.get(index)).itemX(),
                    uiSlot(managedSlots.get(index)).itemY()
            ));
        }
        addPlayerInventory(inventory);
    }

    public BlockPos tablePos() {
        return tablePos;
    }

    public VehicleDefinition vehicleDefinition() {
        return vehicleDefinition;
    }

    public List<VehiclePartSlot> managedSlots() {
        return managedSlots;
    }

    public int playerHotbarStart() {
        return managedSlots.size() + 27;
    }

    public ItemStack installedStack(VehiclePartSlot slot) {
        int index = managedSlots.indexOf(slot);
        return index < 0 ? ItemStack.EMPTY : partMirrors.getItem(index);
    }

    public VehicleInstanceData vehicleData() {
        VehicleInstanceData current = openedVehicleData;
        for (VehiclePartSlot slot : managedSlots) {
            current = current.withoutPart(vehicleDefinition, slot);
            ItemStack stack = installedStack(slot);
            if (stack.getItem() instanceof VehiclePartItem partItem) {
                current = current.withPart(vehicleDefinition, slot, partItem.readPart(stack))
                        .getOrElse(current);
            }
        }
        return current;
    }

    public static boolean isManagedSlot(VehiclePartSlot slot) {
        return MANAGED_SLOT_ORDER.contains(slot);
    }

    public static int installButtonId(VehiclePartSlot slot) {
        return slot.ordinal() * ACTIONS_PER_SLOT + INSTALL_ACTION;
    }

    public static int removeButtonId(VehiclePartSlot slot) {
        return slot.ordinal() * ACTIONS_PER_SLOT + REMOVE_TO_CURSOR_ACTION;
    }

    public static int quickRemoveButtonId(VehiclePartSlot slot) {
        return slot.ordinal() * ACTIONS_PER_SLOT + QUICK_REMOVE_ACTION;
    }

    public static AssemblyTableUiSlot uiSlot(VehiclePartSlot slot) {
        return AssemblyTableUiSlot.forPart(slot).fold(
                () -> {
                    throw new IllegalArgumentException("No assembly UI position for " + slot);
                },
                value -> value
        );
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return Pathway.maybe(slotForButton(buttonId))
                .filter(managedSlots::contains)
                .filter(ignored -> stillValid(player))
                .via(slot -> Pathway.nullable(player.level().getBlockEntity(tablePos))
                        .filter(AssemblyTableBlockEntity.class::isInstance)
                        .map(AssemblyTableBlockEntity.class::cast)
                        .map(table -> {
                            AssemblyPartItemHandler handler = new AssemblyPartItemHandler(
                                    table,
                                    player,
                                    managedSlots
                            );
                            int handlerSlot = managedSlots.indexOf(slot);
                            boolean changed;
                            if (buttonId == installButtonId(slot)) {
                                changed = insertHeldPart(player, handler, handlerSlot);
                            } else if (buttonId == removeButtonId(slot)) {
                                changed = extractPartToCursor(player, handler, handlerSlot);
                            } else {
                                changed = extractPartToInventory(player, handler, handlerSlot);
                            }
                            if (changed) {
                                refreshPartMirrors(player);
                                broadcastChanges();
                            }
                            return true;
                        }))
                .getOrElse(false);
    }

    private static Maybe<VehiclePartSlot> slotForButton(int buttonId) {
        for (var slot : MANAGED_SLOT_ORDER) {
            if (buttonId == installButtonId(slot)
                    || buttonId == removeButtonId(slot)
                    || buttonId == quickRemoveButtonId(slot)) {
                return Maybe.some(slot);
            }
        }
        return Maybe.none();
    }

    @Override
    public void broadcastChanges() {
        refreshPartMirrors(menuPlayer);
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int mirrorEnd = managedSlots.size();
        int mainStart = mirrorEnd;
        int hotbarStart = mainStart + 27;
        int playerEnd = hotbarStart + 9;
        if (index < 0 || index >= playerEnd) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        if (index < mirrorEnd) {
            if (!(player.level().getBlockEntity(tablePos)
                    instanceof AssemblyTableBlockEntity table)) {
                return ItemStack.EMPTY;
            }
            AssemblyPartItemHandler handler = new AssemblyPartItemHandler(
                    table,
                    player,
                    managedSlots
            );
            if (!extractPartToInventory(player, handler, index)) {
                return ItemStack.EMPTY;
            }
            refreshPartMirrors(player);
            broadcastChanges();
            return original;
        }

        Maybe<VehiclePartSlot> assemblyTarget = assemblyTarget(source)
                .filter(managedSlots::contains)
                .filter(slot -> installedStack(slot).isEmpty());
        boolean installed = assemblyTarget.fold(
                () -> false,
                target -> {
                    if (!(player.level().getBlockEntity(tablePos)
                            instanceof AssemblyTableBlockEntity table)) {
                        return false;
                    }
                    AssemblyPartItemHandler handler = new AssemblyPartItemHandler(
                            table,
                            player,
                            managedSlots
                    );
                    int handlerSlot = managedSlots.indexOf(target);
                    ItemStack remainder = handler.insertItem(handlerSlot, source, false);
                    int accepted = source.getCount() - remainder.getCount();
                    if (accepted <= 0) {
                        return false;
                    }
                    sourceSlot.setByPlayer(remainder);
                    return true;
                }
        );
        if (installed) {
            refreshPartMirrors(player);
            broadcastChanges();
            return original;
        }

        boolean moved = index < hotbarStart
                ? moveItemStackTo(source, hotbarStart, playerEnd, false)
                : moveItemStackTo(source, mainStart, hotbarStart, false);
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

    private boolean insertHeldPart(
            Player player,
            AssemblyPartItemHandler handler,
            int handlerSlot
    ) {
        boolean fromCursor = !getCarried().isEmpty();
        ItemStack source = fromCursor ? getCarried() : player.getMainHandItem();
        if (source.isEmpty()) {
            return false;
        }
        ItemStack remainder = handler.insertItem(handlerSlot, source, false);
        int accepted = source.getCount() - remainder.getCount();
        if (accepted <= 0) {
            return false;
        }
        if (fromCursor) {
            setCarried(remainder);
        } else {
            player.setItemInHand(InteractionHand.MAIN_HAND, remainder);
        }
        return true;
    }

    private boolean extractPartToInventory(
            Player player,
            AssemblyPartItemHandler handler,
            int handlerSlot
    ) {
        ItemStack simulated = handler.extractItem(handlerSlot, 1, true);
        if (simulated.isEmpty()) {
            return false;
        }
        PlayerMainInvWrapper playerInventory = new PlayerMainInvWrapper(player.getInventory());
        if (!ItemHandlerHelper.insertItemStacked(playerInventory, simulated, true).isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly.inventory_full"
            ), true);
            return false;
        }

        ItemStack extracted = handler.extractItem(handlerSlot, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                playerInventory,
                extracted,
                false
        );
        if (remainder.isEmpty()) {
            return true;
        }

        ItemStack rollbackRemainder = handler.insertItem(handlerSlot, remainder, false);
        if (!rollbackRemainder.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, rollbackRemainder);
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly.rollback_failed"
            ), true);
        }
        return false;
    }

    private boolean extractPartToCursor(
            Player player,
            AssemblyPartItemHandler handler,
            int handlerSlot
    ) {
        if (!getCarried().isEmpty()) {
            return false;
        }
        ItemStack extracted = handler.extractItem(handlerSlot, 1, false);
        if (extracted.isEmpty()) {
            return false;
        }
        setCarried(extracted);
        return true;
    }

    private static Maybe<VehiclePartSlot> assemblyTarget(ItemStack stack) {
        if (stack.getItem() instanceof VehicleEngineItem) {
            return Maybe.some(VehiclePartSlot.ENGINE);
        }
        if (stack.getItem() instanceof VehiclePowerUnitItem powerItem) {
            return Maybe.some(powerItem.definition().isExtraPowerUnit()
                    ? VehiclePartSlot.EXTRA_POWER_UNIT
                    : VehiclePartSlot.ENERGY_RECOVERY_UNIT);
        }
        return Maybe.none();
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(tablePos).is(ModBlocks.ASSEMBLY_TABLE.get())) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        return player.level().getBlockEntity(tablePos) instanceof AssemblyTableBlockEntity table
                && table.refreshAssemblySession(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()
                && player.level().getBlockEntity(tablePos)
                instanceof AssemblyTableBlockEntity table) {
            table.closeAssemblySession(player);
        }
    }

    private void refreshPartMirrors(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (!(player.level().getBlockEntity(tablePos) instanceof AssemblyTableBlockEntity table)) {
            partMirrors.clearContent();
            return;
        }
        for (int index = 0; index < managedSlots.size(); index++) {
            partMirrors.setItem(index, table.partStack(managedSlots.get(index)));
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_FRAME_X + PLAYER_SLOT_OFFSET
                                + column * PLAYER_COLUMN_STEP,
                        PLAYER_INVENTORY_FRAME_Y + PLAYER_SLOT_OFFSET
                                + row * PLAYER_ROW_STEP
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    PLAYER_HOTBAR_FRAME_X + PLAYER_SLOT_OFFSET
                            + column * PLAYER_COLUMN_STEP,
                    PLAYER_HOTBAR_FRAME_Y + PLAYER_SLOT_OFFSET
            ));
        }
    }

    private static List<VehiclePartSlot> managedSlotsFor(VehicleDefinition definition) {
        return MANAGED_SLOT_ORDER.stream()
                .filter(definition.supportedSlots()::contains)
                .toList();
    }

    private static VehicleDefinition requireDefinition(ResourceLocation id) {
        return VehicleDefinitions.find(id).fold(
                () -> {
                    throw new IllegalArgumentException(
                            "Unknown vehicle definition in assembly menu: " + id);
                },
                Function.identity()
        );
    }

    private static final class ReadOnlyPartSlot extends Slot {
        ReadOnlyPartSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
