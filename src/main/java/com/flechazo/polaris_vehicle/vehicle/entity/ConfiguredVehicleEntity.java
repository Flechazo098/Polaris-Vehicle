package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class ConfiguredVehicleEntity extends AbstractVehicleEntity {
    public ConfiguredVehicleEntity(
            EntityType<? extends ConfiguredVehicleEntity> entityType,
            Level level,
            VehicleDefinition definition
    ) {
        super(entityType, level, definition);
    }
}
