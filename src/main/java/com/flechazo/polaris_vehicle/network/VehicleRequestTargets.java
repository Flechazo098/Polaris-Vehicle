package com.flechazo.polaris_vehicle.network;

import com.flechazo.hkt.business.control.MaybePath;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

final class VehicleRequestTargets {
    static void riding(ServerPlayer sender, BiConsumer<AbstractVehicleEntity, ServerPlayer> action) {
        Pathway.nullable(sender)
                .via(player -> vehicle(player.getVehicle())
                        .peek(target -> action.accept(target, player)))
                .run();
    }

    static void targeted(ServerPlayer sender, int entityId,
                         BiConsumer<AbstractVehicleEntity, ServerPlayer> action) {
        Pathway.nullable(sender)
                .via(player -> vehicle(player.level().getEntity(entityId))
                        .peek(target -> action.accept(target, player)))
                .run();
    }

    private static MaybePath<AbstractVehicleEntity> vehicle(Object entity) {
        return Pathway.nullable(entity)
                .filter(AbstractVehicleEntity.class::isInstance)
                .map(AbstractVehicleEntity.class::cast);
    }
}
