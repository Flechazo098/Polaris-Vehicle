package com.flechazo.polaris_vehicle.registry;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.entity.CrewDollEntity;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleSeatLayout;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.entity.ConfiguredVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.entity.KartEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.function.Supplier;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            Registries.ENTITY_TYPE,
            PolarisVehicle.MODID
    );

    public static final DeferredHolder<EntityType<?>, EntityType<KartEntity>> KART = ENTITY_TYPES.register(
            "kart",
            location -> EntityType.Builder.of(KartEntity::new, MobCategory.MISC)
                    .sized(1.5F, 1.75F)
                    .passengerAttachments(VehicleSeatLayout.require(
                            VehicleDefinitions.KART_ID
                    ).attachmentArray())
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(location.toString())
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> ELDERLY_CAR = register(
            "elderly_car", VehicleDefinitions.ELDERLY_CAR, 1.95F, 2.45F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> SMALL_BUS = register(
            "small_bus", VehicleDefinitions.SMALL_BUS, 3.75F, 3.05F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> SMALL_CAR = register(
            "small_car", VehicleDefinitions.SMALL_CAR, 2.1F, 2.4F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> TWO_SEAT_CAR = register(
            "two_seat_car", VehicleDefinitions.TWO_SEAT_CAR, 2.2F, 2.5F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> SUV = register(
            "suv", VehicleDefinitions.SUV, 3.6F, 3.05F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> CONVERTIBLE = register(
            "convertible", VehicleDefinitions.CONVERTIBLE, 2.45F, 2.35F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> FORMULA_CAR = register(
            "formula_car", VehicleDefinitions.FORMULA_CAR, 2.75F, 1.55F
    );
    public static final DeferredHolder<EntityType<?>, EntityType<CrewDollEntity>> CREW_DOLL = ENTITY_TYPES.register(
            "crew_doll",
            location -> EntityType.Builder.of(CrewDollEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.8F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(location.toString())
    );

    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> RALLY_CAR = register(
            "rally_car", VehicleDefinitions.RALLY_CAR, 2.75F, 2.35F
    );

    private static final Map<ResourceLocation, Supplier<? extends EntityType<? extends AbstractVehicleEntity>>> VEHICLE_TYPES = Map.of(
            VehicleDefinitions.KART_ID, KART::get,
            VehicleDefinitions.ELDERLY_CAR_ID, ELDERLY_CAR::get,
            VehicleDefinitions.SMALL_BUS_ID, SMALL_BUS::get,
            VehicleDefinitions.SMALL_CAR_ID, SMALL_CAR::get,
            VehicleDefinitions.TWO_SEAT_CAR_ID, TWO_SEAT_CAR::get,
            VehicleDefinitions.SUV_ID, SUV::get,
            VehicleDefinitions.CONVERTIBLE_ID, CONVERTIBLE::get,
            VehicleDefinitions.FORMULA_CAR_ID, FORMULA_CAR::get,
            VehicleDefinitions.RALLY_CAR_ID, RALLY_CAR::get
    );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    public static Maybe<AbstractVehicleEntity> create(ResourceLocation vehicleType, Level level) {
        return Pathway.nullable(vehicleType)
                .via(id -> Pathway.nullable(VEHICLE_TYPES.get(id)))
                .map(Supplier::get)
                .via(type -> Pathway.nullable(type.create(level)))
                .map(AbstractVehicleEntity.class::cast)
                .run();
    }

    private static DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> register(
            String name,
            VehicleDefinition definition,
            float width,
            float height
    ) {
        return ENTITY_TYPES.register(name, location -> EntityType.Builder.<ConfiguredVehicleEntity>of(
                        (entityType, level) -> new ConfiguredVehicleEntity(entityType, level, definition),
                        MobCategory.MISC
                )
                .sized(width, height)
                .passengerAttachments(VehicleSeatLayout.require(
                        definition.id()
                ).attachmentArray())
                .clientTrackingRange(12)
                .updateInterval(1)
                .build(location.toString()));
    }
}
