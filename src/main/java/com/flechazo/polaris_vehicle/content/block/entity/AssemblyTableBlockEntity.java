package com.flechazo.polaris_vehicle.content.block.entity;

import com.flechazo.hkt.Either;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.menu.AssemblyTableMenu;
import com.flechazo.polaris_vehicle.item.VehicleEngineItem;
import com.flechazo.polaris_vehicle.item.VehiclePartItem;
import com.flechazo.polaris_vehicle.item.VehiclePowerUnitItem;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.registry.ModBlockEntities;
import com.flechazo.polaris_vehicle.vehicle.data.*;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
public final class AssemblyTableBlockEntity extends BlockEntity implements MenuProvider {
    private static final String VEHICLE_UUID = "VehicleUuid";
    private static final String OWNER_UUID = "OwnerUuid";
    private static final long SESSION_TIMEOUT_TICKS = 200L;
    private static final double SESSION_DISTANCE_SQUARED = 8.0 * 8.0;
    private UUID vehicleUuid;
    private UUID ownerUuid;
    private UUID operatingPlayerUuid;
    private long sessionExpiresAt;
    private int missingVehicleTicks;

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
    }

    public InteractionResult interact(Player player) {
        if (level == null || level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        expireSessionIfInvalid((ServerLevel) level);
        if (vehicleUuid != null) {
            if (!canOperate(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.assembly_table.occupied"), true);
                return InteractionResult.CONSUME;
            }
            if (player.isSecondaryUseActive()) {
                AbstractVehicleEntity vehicle = findVehicle();
                if (vehicle == null) {
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.assembly_table.invalid_binding"), true);
                } else if (!takeVehicleForCarry(player, vehicle)) {
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.assembly_table.invalid_binding"), true);
                }
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.assembly_table.use_wrench"), true);
            }
            return InteractionResult.CONSUME;
        }

        List<AbstractVehicleEntity> candidates = level.getEntitiesOfClass(
                AbstractVehicleEntity.class,
                new AABB(getBlockPos()).inflate(6.0),
                vehicle -> vehicle.maintenanceState() == VehicleMaintenanceState.CARRIED
                        && vehicle.isCarriedBy(player)
                        && vehicle.getPassengers().isEmpty()
        );
        if (candidates.size() != 1) {
            player.displayClientMessage(Component.translatable(
                    candidates.isEmpty()
                            ? "message.polaris_vehicle.assembly_table.no_vehicle"
                            : "message.polaris_vehicle.assembly_table.multiple_vehicles"), true);
            return InteractionResult.CONSUME;
        }
        AbstractVehicleEntity vehicle = candidates.getFirst();
        if (!vehicle.attachToAssemblyTable(getBlockPos(), player.getUUID())) {
            return InteractionResult.CONSUME;
        }
        vehicleUuid = vehicle.getUUID();
        ownerUuid = player.getUUID();
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.assembly_table.attached"), true);
        return InteractionResult.CONSUME;
    }

    public boolean attachPlacedVehicle(Player player, AbstractVehicleEntity vehicle) {
        if (level == null
                || level.isClientSide()
                || vehicleUuid != null
                || !vehicle.attachPlacedItemToAssemblyTable(getBlockPos(), player.getUUID())) {
            return false;
        }
        vehicleUuid = vehicle.getUUID();
        ownerUuid = player.getUUID();
        missingVehicleTicks = 0;
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.assembly_table.attached"), true);
        return true;
    }

    public boolean takeVehicleForCarry(Player player, AbstractVehicleEntity vehicle) {
        if (level == null
                || level.isClientSide()
                || vehicleUuid == null
                || !vehicleUuid.equals(vehicle.getUUID())
                || !canOperate(player)
                || !vehicle.carryFromAssemblyTable(getBlockPos(), player)) {
            return false;
        }
        vehicleUuid = null;
        ownerUuid = null;
        operatingPlayerUuid = null;
        sessionExpiresAt = 0L;
        missingVehicleTicks = 0;
        setChanged();
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.assembly_table.released"), true);
        return true;
    }

    public boolean hasAttachedVehicle() {
        return vehicleUuid != null;
    }

    public void openAssemblySession(Player player, AbstractVehicleEntity vehicle) {
        if (!(level instanceof ServerLevel serverLevel)
                || vehicleUuid == null
                || !vehicleUuid.equals(vehicle.getUUID())
                || !vehicle.isAttachedToAssemblyTable(getBlockPos())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.invalid_binding"), true);
            return;
        }
        expireSessionIfInvalid(serverLevel);
        if (!canOperate(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.occupied"), true);
            return;
        }
        if (operatingPlayerUuid != null && !operatingPlayerUuid.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.session_busy"), true);
            return;
        }
        operatingPlayerUuid = player.getUUID();
        sessionExpiresAt = serverLevel.getGameTime() + SESSION_TIMEOUT_TICKS;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> {
                buffer.writeBlockPos(getBlockPos());
                buffer.writeResourceLocation(vehicle.definition().id());
                VehicleInstanceData.STREAM_CODEC.encode(buffer, vehicle.vehicleData());
            });
        }
    }

    public boolean refreshAssemblySession(Player player) {
        if (!(level instanceof ServerLevel serverLevel)
                || operatingPlayerUuid == null
                || !operatingPlayerUuid.equals(player.getUUID())
                || vehicleUuid == null
                || !canOperate(player)) {
            return false;
        }
        sessionExpiresAt = serverLevel.getGameTime() + SESSION_TIMEOUT_TICKS;
        return true;
    }

    public void closeAssemblySession(Player player) {
        if (operatingPlayerUuid != null
                && operatingPlayerUuid.equals(player.getUUID())) {
            operatingPlayerUuid = null;
            sessionExpiresAt = 0L;
        }
    }

    public boolean authorizePartTransfer(Player player, AbstractVehicleEntity vehicle) {
        if (!(level instanceof ServerLevel serverLevel)
                || vehicleUuid == null
                || !vehicleUuid.equals(vehicle.getUUID())
                || !vehicle.isAttachedToAssemblyTable(getBlockPos())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.invalid_binding"), true);
            return false;
        }
        expireSessionIfInvalid(serverLevel);
        if (!canOperate(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.session_busy"), true);
            return false;
        }
        return true;
    }

    public ItemStack partStack(VehiclePartSlot slot) {
        AbstractVehicleEntity vehicle = findVehicle();
        if (vehicle == null || !vehicle.isAttachedToAssemblyTable(getBlockPos())) {
            return ItemStack.EMPTY;
        }
        return vehicle.vehicleData().part(slot)
                .map(VehiclePartItem::stackFor)
                .orElse(ItemStack.EMPTY);
    }
    public ItemStack insertPart(
            Player player,
            VehiclePartSlot slot,
            ItemStack input,
            boolean simulate
    ) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        AbstractVehicleEntity vehicle = vehicleForOperation(player);
        if (vehicle == null || !AssemblyTableMenu.isManagedSlot(slot)) {
            return input;
        }
        VehicleDefinition definition = vehicle.definition();
        if (!definition.supportedSlots().contains(slot)) {
            if (!simulate) {
                displayTransferFailure(player, "Unsupported slot for this vehicle");
            }
            return input;
        }

        if (!(input.getItem() instanceof VehiclePartItem partItem)) {
            if (!simulate) {
                displayTransferFailure(player, "Pick up or hold a compatible engine or power unit");
            }
            return input;
        }
        InstalledPart part = partItem.readPart(input);
        if (slot == VehiclePartSlot.ENGINE && input.getItem() instanceof VehicleEngineItem) {
            if (!definition.supports(slot, part.category())) {
                if (!simulate) {
                    var supported = Component.empty();
                    for (var id : VehiclePartCompatibility
                            .compatiblePartIds(definition, slot)) {
                        if (!supported.getSiblings().isEmpty()) supported.append(Component.translatable(
                                "message.polaris_vehicle.assembly.list_separator"));
                        supported.append(Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
                    }
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.assembly.engine_unsupported", input.getHoverName(), supported), true);
                }
                return input;
            }
            if (vehicle.vehicleData().part(slot).isDefined()) {
                if (!simulate) {
                    var installed = vehicle.vehicleData().part(slot).get();
                    var id = installed.partId();
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.assembly.engine_occupied",
                            Component.translatable("item." + id.getNamespace() + "." + id.getPath())), true);
                }
                return input;
            }
        }
        var installation = switch (slot) {
            case ENGINE -> input.getItem() instanceof VehicleEngineItem
                    ? VehicleAssemblyService.installEngine(definition, vehicle.vehicleData(), part)
                    : Either.<String, VehicleInstanceData>left(
                    "The engine slot only accepts an engine");
            case EXTRA_POWER_UNIT, ENERGY_RECOVERY_UNIT -> {
                if (!(input.getItem() instanceof VehiclePowerUnitItem powerItem)) {
                    yield Either.<String, VehicleInstanceData>left(
                            "This slot only accepts a power unit");
                }
                VehiclePartSlot itemSlot = powerItem.definition().isExtraPowerUnit()
                        ? VehiclePartSlot.EXTRA_POWER_UNIT
                        : VehiclePartSlot.ENERGY_RECOVERY_UNIT;
                yield itemSlot == slot
                        ? VehicleAssemblyService.installPowerUnit(definition, vehicle.vehicleData(), part)
                        : Either.<String, VehicleInstanceData>left(
                        "The held power unit belongs in " + itemSlot);
            }
            default -> Either.<String, VehicleInstanceData>left(
                    "Unsupported assembly slot");
        };

        return Pathway.either(installation)
                .peekLeft(error -> {
                    if (!simulate) {
                        displayTransferFailure(player, error);
                    }
                })
                .map(updated -> {
                    if (!simulate) {
                        vehicle.updateVehicleData(ignored -> updated);
                        player.displayClientMessage(Component.translatable(
                                "message.polaris_vehicle.assembly.part_installed_named", input.getHoverName()), true);
                    }
                    return input.getCount() == 1
                            ? ItemStack.EMPTY
                            : input.copyWithCount(input.getCount() - 1);
                })
                .getOrElse(input);
    }

    public ItemStack extractPart(
            Player player,
            VehiclePartSlot slot,
            int amount,
            boolean simulate
    ) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        AbstractVehicleEntity vehicle = vehicleForOperation(player);
        if (vehicle == null || !AssemblyTableMenu.isManagedSlot(slot)) {
            return ItemStack.EMPTY;
        }
        VehicleInstanceData original = vehicle.vehicleData();
        return original.part(slot).fold(
                () -> {
                    if (!simulate) {
                        displayTransferFailure(player, "No part is installed in this slot");
                    }
                    return ItemStack.EMPTY;
                },
                installed -> {
                    ItemStack returned = VehiclePartItem.stackFor(installed);
                    if (returned.isEmpty()) {
                        PolarisVehicle.LOGGER.error(
                                "Cannot return unregistered assembly part {} from entity {}",
                                installed.partId(),
                                vehicle.getId()
                        );
                        if (!simulate) {
                            displayTransferFailure(
                                    player,
                                    "The installed part has no valid item form"
                            );
                        }
                        return ItemStack.EMPTY;
                    }

                    return Pathway.either(VehicleAssemblyService.removePart(
                            vehicle.definition(), original, slot
                    ))
                            .peekLeft(error -> {
                                if (!simulate) {
                                    displayTransferFailure(player, error);
                                }
                            })
                            .map(updated -> {
                                ItemStack result = returned.copyWithCount(
                                        Math.min(amount, returned.getCount())
                                );
                                if (!simulate) {
                                    vehicle.updateVehicleData(ignored -> updated);
                                    player.displayClientMessage(Component.translatable(
                                                    "message.polaris_vehicle.assembly.part_removed"),
                                            true
                                    );
                                }
                                return result;
                            })
                            .getOrElse(ItemStack.EMPTY);
                }
        );
    }

    private AbstractVehicleEntity vehicleForOperation(Player player) {
        if (!refreshAssemblySession(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.invalid_binding"), true);
            return null;
        }
        AbstractVehicleEntity vehicle = findVehicle();
        if (vehicle == null || !vehicle.isAttachedToAssemblyTable(getBlockPos())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.invalid_binding"), true);
            return null;
        }
        return vehicle;
    }

    private static void displayTransferFailure(Player player, String reason) {
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.assembly.part_transfer_failed",
                reason
        ), true);
    }

    public boolean recoverVehicleBinding(AbstractVehicleEntity vehicle, UUID recoveredOwner) {
        if (vehicleUuid != null && !vehicleUuid.equals(vehicle.getUUID())) {
            return false;
        }
        if (vehicleUuid == null) {
            vehicleUuid = vehicle.getUUID();
            ownerUuid = recoveredOwner;
            setChanged();
        } else if (ownerUuid == null && recoveredOwner != null) {
            ownerUuid = recoveredOwner;
            setChanged();
        }
        missingVehicleTicks = 0;
        return true;
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            AssemblyTableBlockEntity table
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        table.expireSessionIfInvalid(serverLevel);
        table.validateVehicleBinding(serverLevel);
    }

    private void expireSessionIfInvalid(ServerLevel level) {
        if (operatingPlayerUuid == null) {
            return;
        }
        Player operator = level.getPlayerByUUID(operatingPlayerUuid);
        boolean invalid = operator == null
                || !operator.isAlive()
                || operator.distanceToSqr(
                getBlockPos().getX() + 0.5,
                getBlockPos().getY() + 0.5,
                getBlockPos().getZ() + 0.5
        ) > SESSION_DISTANCE_SQUARED
                || level.getGameTime() >= sessionExpiresAt;
        if (invalid) {
            operatingPlayerUuid = null;
            sessionExpiresAt = 0L;
        }
    }

    private void validateVehicleBinding(ServerLevel level) {
        if (vehicleUuid == null) {
            List<AbstractVehicleEntity> recoverable = level.getEntitiesOfClass(
                    AbstractVehicleEntity.class,
                    new AABB(getBlockPos()).inflate(1.5),
                    vehicle -> vehicle.isAttachedToAssemblyTable(getBlockPos())
            );
            if (recoverable.size() == 1) {
                recoverVehicleBinding(recoverable.getFirst(), null);
            }
            return;
        }
        AbstractVehicleEntity vehicle = findVehicle();
        if (vehicle != null && vehicle.isAttachedToAssemblyTable(getBlockPos())) {
            missingVehicleTicks = 0;
            return;
        }
        missingVehicleTicks++;
        if (missingVehicleTicks >= 100) {
            vehicleUuid = null;
            ownerUuid = null;
            operatingPlayerUuid = null;
            sessionExpiresAt = 0L;
            missingVehicleTicks = 0;
            setChanged();
        }
    }

    public void releaseVehicle() {
        if (level == null || level.isClientSide() || vehicleUuid == null) {
            return;
        }
        AbstractVehicleEntity vehicle = findVehicle();
        if (vehicle != null) {
            vehicle.detachFromAssemblyTable(getBlockPos());
        }
        vehicleUuid = null;
        ownerUuid = null;
        operatingPlayerUuid = null;
        sessionExpiresAt = 0L;
        missingVehicleTicks = 0;
    }

    public void forgetDismantledVehicle(AbstractVehicleEntity vehicle) {

        if (level == null
                || level.isClientSide()
                || vehicleUuid == null
                || !vehicleUuid.equals(vehicle.getUUID())) {
            PolarisVehicle.LOGGER.warn("[vehicle-dismantle] stage=table_clear_failed tablePos={}"
                            + " reason=invalid_side_or_binding boundVehicleUuid={} requestedVehicleUuid={}",
                    getBlockPos(), vehicleUuid, vehicle.getUUID());
            return;
        }
        vehicleUuid = null;
        ownerUuid = null;
        operatingPlayerUuid = null;
        sessionExpiresAt = 0L;
        missingVehicleTicks = 0;
        setChanged();

    }

    private AbstractVehicleEntity findVehicle() {
        if (level == null || vehicleUuid == null) {
            return null;
        }
        var entity = level instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(vehicleUuid)
                : null;
        return entity instanceof AbstractVehicleEntity vehicle ? vehicle : null;
    }

    private boolean canOperate(Player player) {
        return operatingPlayerUuid == null
                || player.getUUID().equals(operatingPlayerUuid);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.polaris_vehicle.assembly_table");
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory inventory,
            Player player
    ) {
        AbstractVehicleEntity vehicle = findVehicle();
        VehicleDefinition definition = vehicle == null
                ? VehicleDefinitions.KART
                : vehicle.definition();
        VehicleInstanceData data = vehicle == null
                ? VehicleInstanceData.create(definition)
                : vehicle.vehicleData();
        return new AssemblyTableMenu(containerId, inventory, getBlockPos(), definition, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (vehicleUuid != null) {
            tag.putUUID(VEHICLE_UUID, vehicleUuid);
        }
        if (ownerUuid != null) {
            tag.putUUID(OWNER_UUID, ownerUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        vehicleUuid = tag.hasUUID(VEHICLE_UUID) ? tag.getUUID(VEHICLE_UUID) : null;
        ownerUuid = tag.hasUUID(OWNER_UUID) ? tag.getUUID(OWNER_UUID) : null;
    }
}
