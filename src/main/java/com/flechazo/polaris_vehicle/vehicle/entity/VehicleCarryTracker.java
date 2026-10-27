package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = PolarisVehicle.MODID)
public final class VehicleCarryTracker {
    private static final TicketType<UUID> CARRIED = TicketType.create("polaris_carried_vehicle", UUID::compareTo);
    private static final Map<MinecraftServer, Map<UUID, AbstractVehicleEntity>> BY_CARRIER = new IdentityHashMap<>();
    private static final Map<AbstractVehicleEntity, Lease> LEASES = new IdentityHashMap<>();

    static void track(AbstractVehicleEntity vehicle, UUID carrier) {
        if (!(vehicle.level() instanceof ServerLevel level) || carrier == null) return;
        BY_CARRIER.computeIfAbsent(level.getServer(), ignored -> new HashMap<>()).put(carrier, vehicle);
        refreshTicket(vehicle);
    }

    static void refreshTicket(AbstractVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) return;
        ChunkPos chunk = vehicle.chunkPosition();
        Lease previous = LEASES.get(vehicle);
        if (previous != null && previous.level == level && previous.chunk.equals(chunk)) return;
        level.getChunkSource().addRegionTicket(CARRIED, chunk, 2, vehicle.getUUID());
        LEASES.put(vehicle, new Lease(level, chunk));
        if (previous != null) previous.level.getChunkSource().removeRegionTicket(
                CARRIED, previous.chunk, 2, vehicle.getUUID());
    }

    static void untrack(AbstractVehicleEntity vehicle) {
        if (vehicle.level() instanceof ServerLevel level) {
            var entries = BY_CARRIER.get(level.getServer());
            if (entries != null) {
                entries.values().removeIf(value -> value == vehicle);
                if (entries.isEmpty()) BY_CARRIER.remove(level.getServer());
            }
        }
        Lease previous = LEASES.remove(vehicle);
        if (previous != null) previous.level.getChunkSource().removeRegionTicket(
                CARRIED, previous.chunk, 2, vehicle.getUUID());
    }

    private static void follow(ServerPlayer player) {
        var entries = BY_CARRIER.get(player.server);
        var vehicle = entries == null ? null : entries.get(player.getUUID());
        if (vehicle == null) return;
        if (vehicle.isRemoved() || !vehicle.isCarriedBy(player)) {
            untrack(vehicle);
            return;
        }
        vehicle.synchronizeCarriedTo(player);
    }

    static AbstractVehicleEntity tracked(ServerPlayer player) {
        var entries = BY_CARRIER.get(player.server);
        return entries == null ? null : entries.get(player.getUUID());
    }

    @SubscribeEvent
    public static void afterPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) follow(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) follow(player);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var entries = BY_CARRIER.get(player.server);
        var vehicle = entries == null ? null : entries.get(player.getUUID());
        if (vehicle != null) vehicle.releaseCarriedState();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        BY_CARRIER.remove(event.getServer());
        LEASES.entrySet().removeIf(entry -> entry.getValue().level.getServer() == event.getServer());
    }

    private record Lease(ServerLevel level, ChunkPos chunk) {
    }
}
