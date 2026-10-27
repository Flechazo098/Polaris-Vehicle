package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.world.phys.Vec3;
@NetworkPacket(
        modId = PolarisVehicle.MODID,
        id = "vehicle_maintenance_transform",
        side = Side.CLIENT
)
public record VehicleMaintenanceTransformPacket(
        int entityId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) implements INetworkPacket<VehicleMaintenanceTransformPacket> {
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
                .ifPresent(vehicle -> vehicle.applyMaintenanceTransform(
                        new Vec3(x, y, z),
                        yaw,
                        pitch
                )));
    }
}
