package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.item.VehicleHammerItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = PolarisVehicle.MODID)
public final class VehicleDismantleDiagnostics {

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.getMainHandItem().getItem() instanceof VehicleHammerItem)) return;
        var target = event.getTarget();
        PolarisVehicle.LOGGER.debug("[vehicle-dismantle] stage=server_attack player={} target={} targetType={}"
                        + " vehicleType={} canceled={} attackable={} pickable={} creative={} spectator={}"
                        + " playerPos={} targetPos={} distanceSq={} dimension={} tick={}",
                player.getGameProfile().getName(), target.getId(), BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                target instanceof AbstractVehicleEntity vehicle ? vehicle.vehicleTypeId() : "not_a_vehicle",
                event.isCanceled(), target.isAttackable(), target.isPickable(), player.getAbilities().instabuild,
                player.isSpectator(), player.position(), target.position(), player.distanceToSqr(target),
                player.level().dimension().location(), player.level().getGameTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackTable(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof VehicleHammerItem)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof AssemblyTableBlockEntity table)) return;
        PolarisVehicle.LOGGER.debug("[vehicle-dismantle] stage=server_hit_table player={} tablePos={}"
                        + " hasAttachedVehicle={} canceled={} useBlock={} useItem={} dimension={} tick={}",
                player.getGameProfile().getName(), event.getPos(), table.hasAttachedVehicle(), event.isCanceled(),
                event.getUseBlock(), event.getUseItem(), player.level().dimension().location(), player.level().getGameTime());
    }
}
