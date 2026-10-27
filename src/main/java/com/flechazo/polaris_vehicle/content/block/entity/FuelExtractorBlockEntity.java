package com.flechazo.polaris_vehicle.content.block.entity;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.menu.FuelExtractorMenu;
import com.flechazo.polaris_vehicle.fuel.*;
import com.flechazo.polaris_vehicle.item.VehicleFuelTankItem;
import com.flechazo.polaris_vehicle.registry.ModBlockEntities;
import com.flechazo.polaris_vehicle.registry.ModRecipes;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
public final class FuelExtractorBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int BLAZE_ROD_SLOT = 0;
    public static final int COAL_SLOT = 1;
    public static final int COMBUSTIBLE_SLOT = 2;
    public static final int TANK_SLOT = 3;
    public static final int SLOT_COUNT = 4;
    public static final int FUEL_SEGMENT_CAPACITY_ML = 7_200;
    public static final int FUEL_SEGMENT_COUNT = 3;
    public static final int FUEL_CAPACITY_ML = FUEL_SEGMENT_CAPACITY_ML * FUEL_SEGMENT_COUNT;
    private static final String FUEL_BUFFER = "FuelBuffer";
    private static final String PROGRESS = "Progress";
    private static final String PENDING_OUTPUT = "PendingOutputMl";
    private int pendingOutputMl;
    private static final String ACTIVE_RECIPE = "ActiveRecipe";
    private static final String BURN_TIME_REMAINING = "BurnTimeRemaining";
    private static final String BURN_TIME_TOTAL = "BurnTimeTotal";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private FuelBufferData fuelBuffer = FuelBufferData.empty();
    private int progress;
    private int burnTimeRemaining;
    private int burnTimeTotal;
    @Nullable
    private ResourceLocation activeRecipeId;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> totalProgress();
                case 2 -> fuelBuffer.amountMl();
                case 3 -> FUEL_CAPACITY_ML;
                case 4 -> burnTimeRemaining;
                case 5 -> burnTimeTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return FuelExtractorMenu.DATA_COUNT;
        }
    };

    public FuelExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_EXTRACTOR.get(), pos, state);
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            FuelExtractorBlockEntity extractor
    ) {
        extractor.tickExtraction();
        if (level.getGameTime() % 20L == 0L) {
            extractor.fillPortableTank();
        }
    }

    public int storedFuelMl() {
        return fuelBuffer.amountMl();
    }

    public FuelBufferData fuelBuffer() {
        return fuelBuffer;
    }

    public int progress() {
        return progress;
    }

    public int totalProgress() {
        if (level == null || activeRecipeId == null) {
            return 0;
        }
        return level.getRecipeManager().byKey(activeRecipeId)
                .filter(holder -> holder.value() instanceof FuelExtractorRecipe)
                .map(holder -> ((FuelExtractorRecipe) holder.value()).processingTicks())
                .orElse(0);
    }

    public void openMenu(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        }
    }

    private void tickExtraction() {
        if (pendingOutputMl > 0) {
            int accepted = Math.min(pendingOutputMl, FUEL_CAPACITY_ML - fuelBuffer.amountMl());
            if (accepted > 0) {
                fuelBuffer = fuelBuffer.withContents(fuelBuffer.fuelType(),
                        fuelBuffer.amountMl() + accepted, FUEL_CAPACITY_ML);
                pendingOutputMl -= accepted;
                changedAndSync();
            }
            if (pendingOutputMl > 0) return;
        }
        int previousBurnTime = burnTimeRemaining;
        ItemStack blazeRods = items.get(BLAZE_ROD_SLOT);
        ItemStack coal = items.get(COAL_SLOT);
        if (level == null) {
            return;
        }
        FuelExtractorRecipeInput recipeInput = new FuelExtractorRecipeInput(blazeRods, coal);
        var recipeHolder = level.getRecipeManager().getRecipeFor(
                ModRecipes.FUEL_EXTRACTION_TYPE.get(),
                recipeInput,
                level,
                activeRecipeId
        );
        if (recipeHolder.isEmpty()) {
            resetProgress();
            return;
        }
        FuelExtractorRecipe recipe = recipeHolder.get().value();
        if (!fuelBuffer.canAccept(recipe.fuelType())) {
            resetProgress();
            return;
        }
        if (!recipeHolder.get().id().equals(activeRecipeId)) {
            activeRecipeId = recipeHolder.get().id();
            progress = 0;
        }
        if (fuelBuffer.amountMl() >= FUEL_CAPACITY_ML) {
            resetProgress();
            return;
        }
        if (burnTimeRemaining <= 0 && !igniteNextCombustible()) {
            return;
        }
        burnTimeRemaining--;
        FuelExtractorProcess.StepResult result = FuelExtractorProcess.tick(
                progress,
                fuelBuffer.amountMl(),
                FUEL_CAPACITY_ML,
                recipe,
                recipe.matches(recipeInput, level)
        );
        boolean changed = result.progress() != progress
                || result.storedFuelMl() != fuelBuffer.amountMl()
                || burnTimeRemaining != previousBurnTime;
        progress = result.progress();
        pendingOutputMl = result.pendingOutputMl();
        fuelBuffer = fuelBuffer.withContents(
                recipe.fuelType(),
                result.storedFuelMl(),
                FUEL_CAPACITY_ML
        );
        if (result.consumeIngredients()) {
            blazeRods.shrink(recipe.blazeRodCount());
            coal.shrink(recipe.coalCount());
            if (blazeRods.isEmpty()) {
                items.set(BLAZE_ROD_SLOT, ItemStack.EMPTY);
            }
            if (coal.isEmpty()) {
                items.set(COAL_SLOT, ItemStack.EMPTY);
            }
        }
        if (changed || result.consumeIngredients()) {
            setChanged();
            if (result.consumeIngredients() || progress % 10 == 0) {
                syncBlock();
            }
        }
    }

    private boolean igniteNextCombustible() {
        ItemStack combustible = items.get(COMBUSTIBLE_SLOT);
        int burnTime = combustible.getBurnTime(null);
        if (combustible.isEmpty() || burnTime <= 0) {
            burnTimeRemaining = 0;
            burnTimeTotal = 0;
            return false;
        }
        burnTimeRemaining = burnTime;
        burnTimeTotal = burnTime;
        if (combustible.hasCraftingRemainingItem()) {
            items.set(COMBUSTIBLE_SLOT, combustible.getCraftingRemainingItem());
        } else {
            combustible.shrink(1);
            if (combustible.isEmpty()) {
                items.set(COMBUSTIBLE_SLOT, ItemStack.EMPTY);
            }
        }
        changedAndSync();
        return true;
    }

    private void fillPortableTank() {
        ItemStack tankStack = items.get(TANK_SLOT);
        if (fuelBuffer.amountMl() <= 0
                || !(tankStack.getItem() instanceof VehicleFuelTankItem tankItem)) {
            return;
        }
        PortableFuelTankData tank = tankItem.readData(tankStack);
        if (tank.amountMl() > 0 && !tank.fuelType().equals(fuelBuffer.fuelType())) {
            return;
        }
        int amount = Math.min(
                tankItem.transferPerIntervalMl(),
                Math.min(fuelBuffer.amountMl(), tankItem.capacityMl() - tank.amountMl())
        );
        if (amount <= 0) {
            return;
        }
        fuelBuffer = fuelBuffer.withContents(
                fuelBuffer.fuelType(),
                fuelBuffer.amountMl() - amount,
                FUEL_CAPACITY_ML
        );
        tankItem.writeData(tankStack, new PortableFuelTankData(
                fuelBuffer.fuelType(),
                tank.amountMl() + amount
        ).sanitized(tankItem.capacityMl()));
        changedAndSync();
    }

    private void resetProgress() {
        if (progress == 0 && activeRecipeId == null) {
            return;
        }
        progress = 0;
        activeRecipeId = null;
        changedAndSync();
    }

    private void changedAndSync() {
        setChanged();
        syncBlock();
    }

    private void syncBlock() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.put(FUEL_BUFFER, FuelBufferData.CODEC.encodeStart(NbtOps.INSTANCE, fuelBuffer).getOrThrow());
        tag.putInt(PROGRESS, progress);
        tag.putInt(PENDING_OUTPUT, pendingOutputMl);
        tag.putInt(BURN_TIME_REMAINING, burnTimeRemaining);
        tag.putInt(BURN_TIME_TOTAL, burnTimeTotal);
        if (activeRecipeId != null) {
            tag.putString(ACTIVE_RECIPE, activeRecipeId.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.contains(FUEL_BUFFER)) {
            fuelBuffer = Pathway.either(CodecUtil.toEither(FuelBufferData.CODEC.parse(
                            NbtOps.INSTANCE,
                            tag.get(FUEL_BUFFER)
                    ))).peekLeft(error -> PolarisVehicle.LOGGER.warn(
                            "Invalid fuel extractor buffer at {}: {}",
                            worldPosition,
                            error
                    )).map(data -> data.sanitized(FUEL_CAPACITY_ML))
                    .getOrElse(FuelBufferData.empty());
        } else {
            fuelBuffer = FuelBufferData.empty();
        }
        progress = Math.max(0, tag.getInt(PROGRESS));
        pendingOutputMl = Math.max(0, tag.getInt(PENDING_OUTPUT));
        burnTimeRemaining = Math.max(0, tag.getInt(BURN_TIME_REMAINING));
        burnTimeTotal = Math.max(burnTimeRemaining, tag.getInt(BURN_TIME_TOTAL));
        activeRecipeId = tag.contains(ACTIVE_RECIPE)
                ? ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) changedAndSync();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        changedAndSync();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case BLAZE_ROD_SLOT -> acceptsRecipeIngredient(stack, true);
            case COAL_SLOT -> acceptsRecipeIngredient(stack, false);
            case COMBUSTIBLE_SLOT -> stack.getBurnTime(null) > 0;
            case TANK_SLOT -> acceptsPortableTank(stack);
            default -> false;
        };
    }

    private boolean acceptsPortableTank(ItemStack stack) {
        if (!(stack.getItem() instanceof VehicleFuelTankItem tankItem)) return false;
        var tank = tankItem.readData(stack);
        return tank.amountMl() < tankItem.capacityMl()
                && (tank.amountMl() == 0 || fuelBuffer.amountMl() == 0 && pendingOutputMl == 0
                || tank.fuelType().equals(fuelBuffer.fuelType()));
    }

    private boolean acceptsRecipeIngredient(ItemStack stack, boolean blazeRodSlot) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        return level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.FUEL_EXTRACTION_TYPE.get())
                .stream()
                .map(RecipeHolder::value)
                .anyMatch(recipe -> blazeRodSlot
                        ? recipe.acceptsBlazeRods(stack)
                        : recipe.acceptsCoal(stack));
    }

    @Override
    public void clearContent() {
        items.clear();
        changedAndSync();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.polaris_vehicle.fuel_extractor");
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory inventory,
            Player player
    ) {
        return new FuelExtractorMenu(
                containerId,
                inventory,
                this,
                menuData,
                worldPosition
        );
    }
}
