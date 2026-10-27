package com.flechazo.polaris_vehicle.item;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.block.AssemblyTableBlock;
import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.registry.ModEntityTypes;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePartCompatibility;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class VehicleItem extends Item {
    private static final String DATA_KEY = "PolarisVehicleInstance";
    private final VehicleDefinition definition;

    public VehicleItem(Properties properties, VehicleDefinition definition) {
        super(properties);
        this.definition = definition;
    }

    public VehicleDefinition definition() {
        return definition;
    }

    public VehicleInstanceData readData(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(DATA_KEY)) {
            return VehicleInstanceData.create(definition);
        }
        return Pathway.either(CodecUtil.toEither(
                        VehicleInstanceData.CODEC.parse(NbtOps.INSTANCE, root.get(DATA_KEY))))
                .peekLeft(error -> PolarisVehicle.LOGGER.warn(
                        "Invalid vehicle item data for {}: {}",
                        definition.id(),
                        error
                ))
                .via(data -> definition.id().equals(data.vehicleType())
                        ? Pathway.right(data)
                        : Pathway.left(
                        "Vehicle item type does not match stored instance type"
                ))
                .map(data -> data.sanitized(definition))
                .getOrElse(VehicleInstanceData.create(definition));
    }

    public void writeData(ItemStack stack, VehicleInstanceData data) {
        if (!definition.id().equals(data.vehicleType())) {
            throw new IllegalArgumentException(
                    "Cannot store " + data.vehicleType() + " in item for " + definition.id()
            );
        }
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.put(
                DATA_KEY,
                VehicleInstanceData.CODEC.encodeStart(NbtOps.INSTANCE, data.sanitized(definition)).getOrThrow()
        );
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public ItemStack createStack(VehicleInstanceData data) {
        ItemStack stack = new ItemStack(this);
        writeData(stack, data);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        return Pathway.maybe(ModEntityTypes.create(definition.id(), serverLevel))
                .map(vehicle -> placeVehicle(context, serverLevel, vehicle))
                .getOrElse(InteractionResult.FAIL);
    }

    private InteractionResult placeVehicle(
            UseOnContext context, ServerLevel serverLevel, AbstractVehicleEntity vehicle
    ) {
        if (serverLevel.getBlockEntity(context.getClickedPos())
                instanceof AssemblyTableBlockEntity table) {
            return placeOnAssemblyTable(context, serverLevel, table, vehicle);
        }
        if (context.getClickedFace() != Direction.UP) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        BlockPos placementBlock = context.getClickedPos().relative(context.getClickedFace());
        Vec3 placement = Vec3.atBottomCenterOf(placementBlock);
        float placementYaw = context.getPlayer() == null
                ? -90.0F
                : context.getPlayer().getYRot() - 90.0F;
        vehicle.initializePlacementPose(placement, placementYaw);
        if (context.getPlayer() != null
                && vehicle.getBoundingBox().inflate(0.25)
                .intersects(context.getPlayer().getBoundingBox())) {
            context.getPlayer().displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.placement.too_close"), true);
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        if (!vehicle.restoreFromItem(readData(context.getItemInHand()))
                || !serverLevel.noCollision(vehicle)
                || !serverLevel.getWorldBorder().isWithinBounds(vehicle.getBoundingBox())
                || !serverLevel.addFreshEntity(vehicle)) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private InteractionResult placeOnAssemblyTable(
            UseOnContext context,
            ServerLevel serverLevel,
            AssemblyTableBlockEntity table,
            AbstractVehicleEntity vehicle
    ) {
        if (context.getPlayer() == null || table.hasAttachedVehicle()) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        float requestedYaw = context.getPlayer().getYRot() - 90.0F;
        float alignedYaw = AssemblyTableBlock.alignedVehicleYaw(
                table.getBlockState(),
                requestedYaw
        );
        vehicle.initializePlacementPose(
                Vec3.atCenterOf(context.getClickedPos()),
                alignedYaw
        );
        if (!vehicle.restoreFromItem(readData(context.getItemInHand()))
                || !serverLevel.addFreshEntity(vehicle)
                || !table.attachPlacedVehicle(context.getPlayer(), vehicle)) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        VehicleInstanceData data = readData(stack);
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.vehicle_item.fuel",
                data.fuel(),
                definition.fuelCapacity()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.polaris_vehicle.vehicle_item.parts",
                data.parts().size()
        ).withStyle(ChatFormatting.DARK_GRAY));
        if (!VehicleTooltips.details(tooltip, flag)) return;
        appendCompatibility(tooltip, VehiclePartSlot.ENGINE,
                "tooltip.polaris_vehicle.vehicle_item.compatible_engines");
        if (!definition.wheelSlots().isEmpty()) {
            appendCompatibility(tooltip, definition.wheelSlots().getFirst(),
                    "tooltip.polaris_vehicle.vehicle_item.compatible_tires");
        }
        appendCompatibility(tooltip, VehiclePartSlot.EXTRA_POWER_UNIT,
                "tooltip.polaris_vehicle.vehicle_item.compatible_extra_power");
        appendCompatibility(tooltip, VehiclePartSlot.ENERGY_RECOVERY_UNIT,
                "tooltip.polaris_vehicle.vehicle_item.compatible_recovery");
    }

    private void appendCompatibility(List<Component> tooltip, VehiclePartSlot slot, String key) {
        List<ResourceLocation> parts = VehiclePartCompatibility.compatiblePartIds(definition, slot);
        if (parts.isEmpty()) return;
        tooltip.add(VehicleTooltips.heading(Component.translatable(key)));
        for (int index = 0; index < parts.size(); index += 2) {
            var line = Component.literal(VehicleTooltips.INDENT);
            for (int part = index; part < Math.min(index + 2, parts.size()); part++) {
                if (part > index)
                    line.append(Component.translatable("tooltip.polaris_vehicle.vehicle_item.part_separator"));
                ResourceLocation id = parts.get(part);
                line.append(Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
            }
            tooltip.add(line.withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
