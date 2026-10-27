package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

@EventBusSubscriber(modid = PolarisVehicle.MODID)
public final class VehicleDismantleNotices extends SavedData {
    private static final String DATA_NAME = PolarisVehicle.MODID + "_dismantle_notices";
    private static final SavedData.Factory<VehicleDismantleNotices> FACTORY =
            new SavedData.Factory<>(VehicleDismantleNotices::new, VehicleDismantleNotices::load, null);

    private record Notice(String vehicleName, String dismantlerName, String dimension, BlockPos pos) {
        Component message(boolean whileAway) {
            return Component.translatable(
                    whileAway ? "message.polaris_vehicle.dismantle.owner_notice_offline"
                            : "message.polaris_vehicle.dismantle.owner_notice",
                    Component.translatable(vehicleName),
                    dimension,
                    pos.getX(), pos.getY(), pos.getZ(),
                    Component.literal(dismantlerName).withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.RED);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Vehicle", vehicleName);
            tag.putString("Dismantler", dismantlerName);
            tag.putString("Dimension", dimension);
            tag.putInt("X", pos.getX());
            tag.putInt("Y", pos.getY());
            tag.putInt("Z", pos.getZ());
            return tag;
        }

        static Notice load(CompoundTag tag) {
            return new Notice(tag.getString("Vehicle"), tag.getString("Dismantler"), tag.getString("Dimension"),
                    new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")));
        }
    }

    private final Map<UUID, List<Notice>> pending = new HashMap<>();

    static void vehicleDismantled(AbstractVehicleEntity vehicle, ServerPlayer dismantler, UUID ownerUuid) {
        if (ownerUuid == null || ownerUuid.equals(dismantler.getUUID())) return;
        MinecraftServer server = dismantler.getServer();
        if (server == null) return;
        Notice notice = new Notice(vehicle.getType().getDescriptionId(), dismantler.getGameProfile().getName(),
                vehicle.level().dimension().location().toString(), vehicle.blockPosition());
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
        if (owner != null) {
            owner.sendSystemMessage(notice.message(false));
            return;
        }
        VehicleDismantleNotices notices = get(server);
        notices.pending.computeIfAbsent(ownerUuid, ignored -> new ArrayList<>()).add(notice);
        notices.setDirty();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        VehicleDismantleNotices notices = get(player.getServer());
        List<Notice> waiting = notices.pending.remove(player.getUUID());
        if (waiting == null) return;
        notices.setDirty();
        for (Notice notice : waiting) player.sendSystemMessage(notice.message(true));
    }

    private static VehicleDismantleNotices get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static VehicleDismantleNotices load(CompoundTag tag, HolderLookup.Provider registries) {
        VehicleDismantleNotices notices = new VehicleDismantleNotices();
        CompoundTag owners = tag.getCompound("Owners");
        for (String key : owners.getAllKeys()) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            List<Notice> list = new ArrayList<>();
            for (Tag entry : owners.getList(key, Tag.TAG_COMPOUND)) list.add(Notice.load((CompoundTag) entry));
            if (!list.isEmpty()) notices.pending.put(owner, list);
        }
        return notices;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        pending.forEach((owner, list) -> {
            ListTag entries = new ListTag();
            for (Notice notice : list) entries.add(notice.save());
            owners.put(owner.toString(), entries);
        });
        tag.put("Owners", owners);
        return tag;
    }
}
