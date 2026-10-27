package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleParameterEditorData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_parameters_update", side = Side.SERVER)
public record VehicleParametersUpdatePacket(ResourceLocation vehicleType, String json, String kind, String action)
        implements INetworkPacket<VehicleParametersUpdatePacket> {

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_DIRECTORY = "polaris_vehicle_vehicle_parameters";

    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide() || context.sender() == null) {
            return;
        }
        context.enqueueWork(() -> applyOnServer(context.sender()));
    }

    public void applyOnServer(ServerPlayer player) {
        if (player == null) return;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.vehicle_parameters.permission_denied"
            ).withStyle(ChatFormatting.RED), false);
            return;
        }
        Pathway.tryOf(() -> VehicleParameterEditorData.updates(kind, vehicleType, json, action))
                .peekChecked(updates -> writeParameterPack(player, updates))
                .peekChecked(ignored -> reloadParameterPack(player))
                .peekFailure(exception -> {
                    PolarisVehicle.LOGGER.warn("Rejected vehicle parameter update for {}", vehicleType, exception);
                    player.displayClientMessage(Component.literal("Invalid vehicle parameters: "
                            + exception.getMessage()).withStyle(ChatFormatting.RED), false);
                })
                .run();
    }

    private static void writeParameterPack(ServerPlayer player, Map<String, JsonElement> updates) throws IOException {
        Path dataPacks = player.server.getWorldPath(LevelResource.DATAPACK_DIR);
        Path packRoot = dataPacks.resolve(PACK_DIRECTORY);
        for (var entry : updates.entrySet()) {
            Path target = packRoot.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "parameters-", ".tmp");
            try {
                Files.writeString(temporary, PRETTY_GSON.toJson(entry.getValue()) + System.lineSeparator(), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        Path metadata = packRoot.resolve("pack.mcmeta");
        if (Files.notExists(metadata)) {
            Files.writeString(metadata, """
                        {
                          "pack": {
                            "pack_format": 48,
                            "description": "Polaris Vehicle parameter overrides"
                          }
                        }
                        """, StandardCharsets.UTF_8);
        }
    }

    private void reloadParameterPack(ServerPlayer player) {
        PackRepository repository = player.server.getPackRepository();
        repository.reload();
        repository.addPack("file/" + PACK_DIRECTORY);
        var selected = new ArrayList<>(repository.getSelectedIds());
        player.server.reloadResources(selected).whenComplete((ignored, error) -> {
            if (error == null) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.vehicle_parameters.saved",
                        vehicleType.toString()
                ).withStyle(ChatFormatting.GREEN), false);
            } else {
                PolarisVehicle.LOGGER.error("Failed to reload vehicle parameter data pack", error);
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.vehicle_parameters.reload_failed"
                ).withStyle(ChatFormatting.RED), false);
            }
        });
    }
}
