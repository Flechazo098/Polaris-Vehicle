package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_boost_request", side = Side.SERVER)
public record VehicleBoostRequestPacket(boolean requested) implements INetworkPacket<VehicleBoostRequestPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide() || !requested) {
            return;
        }
        context.enqueueWork(() -> VehicleRequestTargets.riding(
                context.sender(), AbstractVehicleEntity::requestBoost));
    }
}
