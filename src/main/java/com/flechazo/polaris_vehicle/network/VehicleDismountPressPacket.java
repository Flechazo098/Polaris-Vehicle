package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_dismount_press", side = Side.SERVER)
public record VehicleDismountPressPacket(int entityId) implements INetworkPacket<VehicleDismountPressPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide()) return;
        context.enqueueWork(() -> VehicleRequestTargets.riding(context.sender(), (vehicle, sender) -> {
            if (vehicle.getId() == entityId) vehicle.acceptDismountPress(sender);
        }));
    }
}
