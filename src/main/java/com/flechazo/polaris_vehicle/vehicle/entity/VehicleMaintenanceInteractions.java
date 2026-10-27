package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class VehicleMaintenanceInteractions {
    private static boolean registered;

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(VehicleMaintenanceInteractions::onRightClickBlock);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().isEmpty()
                || event.getFace() != Direction.UP
                || event.getLevel().getBlockState(event.getPos()).is(ModBlocks.ASSEMBLY_TABLE)) {
            return;
        }
        var player = event.getEntity();
        Pathway.listPath(event.getLevel().getEntitiesOfClass(
                AbstractVehicleEntity.class,
                new AABB(player.blockPosition()).inflate(8.0),
                vehicle -> vehicle.isCarriedBy(player)
        )).head()
                .map(target -> target.tryPlaceCarriedVehicle(player, event.getHitVec().getLocation()))
                .peek(returned -> {
                    player.displayClientMessage(Component.translatable(returned
                            ? "message.polaris_vehicle.carry.placed"
                            : "message.polaris_vehicle.carry.blocked"), true);
                    if (returned) player.swing(InteractionHand.MAIN_HAND, true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                });
    }
}
