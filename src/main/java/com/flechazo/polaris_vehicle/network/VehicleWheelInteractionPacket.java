package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_wheel_interaction", side = Side.SERVER)
public record VehicleWheelInteractionPacket(int entityId) implements INetworkPacket<VehicleWheelInteractionPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide()) return;
        context.enqueueWork(() -> VehicleRequestTargets.targeted(
                context.sender(), entityId, AbstractVehicleEntity::handleWheelTargetRequest));
    }
}
