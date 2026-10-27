package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.server.level.ServerPlayer;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_horn", side = Side.SERVER)
public record VehicleHornPacket(boolean pressed) implements INetworkPacket<VehicleHornPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide()) {
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer sender = context.sender();
            if (sender != null && sender.getVehicle() instanceof AbstractVehicleEntity vehicle) {
                vehicle.acceptHorn(sender, pressed);
            }
        });
    }
}
