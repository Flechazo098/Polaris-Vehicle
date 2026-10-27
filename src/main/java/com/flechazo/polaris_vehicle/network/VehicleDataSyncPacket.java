package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_data_sync", side = Side.CLIENT)
public record VehicleDataSyncPacket(
        int entityId,
        VehicleInstanceData data
) implements INetworkPacket<VehicleDataSyncPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isClientSide()) {
            return;
        }
        context.enqueueWork(() -> Maybe.ofNullable(context.client())
                .flatMap(client -> Maybe.ofNullable(client.level))
                .flatMap(level -> Maybe.ofNullable(level.getEntity(entityId)))
                .filter(AbstractVehicleEntity.class::isInstance)
                .map(AbstractVehicleEntity.class::cast)
                .ifPresent(vehicle -> vehicle.applyNetworkData(data)));
    }
}
