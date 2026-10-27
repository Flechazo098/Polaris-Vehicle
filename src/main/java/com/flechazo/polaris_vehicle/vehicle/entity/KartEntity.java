package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class KartEntity extends AbstractVehicleEntity {
    public KartEntity(EntityType<? extends KartEntity> entityType, Level level) {
        super(entityType, level, VehicleDefinitions.KART);
    }
}
