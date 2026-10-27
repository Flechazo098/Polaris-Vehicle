package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringResponse;
import net.minecraft.server.level.ServerPlayer;

@NetworkPacket(modId = PolarisVehicle.MODID, id = "vehicle_input", side = Side.SERVER)
public record VehicleInputPacket(
        boolean forward,
        boolean backward,
        boolean left,
        boolean right,
        boolean brake,
        VehicleSteeringResponse steeringResponse
) implements INetworkPacket<VehicleInputPacket> {
    public VehicleInputPacket(boolean forward, boolean backward, boolean left, boolean right, boolean brake) {
        this(forward, backward, left, right, brake,
                VehicleSteeringResponse.DEFAULT);
    }

    @Override
    public void handle(INetworkContext context) {
        if (!context.isServerSide()) {
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer sender = context.sender();
            if (sender != null && sender.getVehicle() instanceof AbstractVehicleEntity vehicle) {
                vehicle.acceptDriverInput(sender, forward, backward, left, right, brake, steeringResponse);
            }
        });
    }
}
