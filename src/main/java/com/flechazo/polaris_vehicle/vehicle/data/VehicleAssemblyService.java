package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Either;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinitions;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;

public final class VehicleAssemblyService {
    public static Either<String, VehicleInstanceData> installEngine(
            VehicleDefinition definition,
            VehicleInstanceData data,
            InstalledPart engine
    ) {
        return Pathway.either(Pathway.nullable(engine).toEither(() -> "Engine must not be null"))
                .via(validEngine -> data.part(VehiclePartSlot.ENGINE).isDefined()
                        ? Pathway.<String, InstalledPart>left("An engine is already installed")
                        : Pathway.<String, InstalledPart>right(validEngine))
                .via(validEngine -> VehicleEngineDefinitions.isKnownEngine(validEngine)
                        ? Pathway.<String, InstalledPart>right(validEngine)
                        : Pathway.<String, InstalledPart>left("Unknown or mismatched engine definition: " + validEngine.partId()))
                .via(validEngine -> Pathway.either(data.withPart(
                        definition,
                        VehiclePartSlot.ENGINE,
                        validEngine
                )))
                .run();
    }

    public static Either<String, VehicleInstanceData> removeEngine(
            VehicleDefinition definition,
            VehicleInstanceData data
    ) {
        return Pathway.either(data.part(VehiclePartSlot.ENGINE).toEither("No engine is installed"))
                .map(engine -> data.withoutPart(definition, VehiclePartSlot.ENGINE))
                .run();
    }

    public static Either<String, VehicleInstanceData> installPowerUnit(
            VehicleDefinition definition,
            VehicleInstanceData data,
            InstalledPart part
    ) {
        return Pathway.either(Pathway.nullable(part).toEither(() -> "Power unit must not be null"))
                .via(validPart -> Pathway.either(VehiclePowerDefinitions.find(validPart.partId())
                        .filter(powerDefinition -> powerDefinition.matches(validPart))
                        .toEither("Unknown or mismatched power-unit definition: " + validPart.partId()))
                        .map(VehicleAssemblyService::slotFor)
                        .via(slot -> data.part(slot).isDefined()
                                ? Pathway.<String, VehicleInstanceData>left("The compatible power-unit slot is already occupied")
                                : Pathway.either(data.withPart(definition, slot, validPart))))
                .run();
    }

    public static Either<String, VehicleInstanceData> removePart(
            VehicleDefinition definition,
            VehicleInstanceData data,
            VehiclePartSlot slot
    ) {
        return Pathway.either(data.part(slot).toEither("No part is installed in " + slot))
                .map(part -> data.withoutPart(definition, slot))
                .run();
    }

    private static VehiclePartSlot slotFor(VehiclePowerDefinition definition) {
        return definition.isExtraPowerUnit()
                ? VehiclePartSlot.EXTRA_POWER_UNIT
                : VehiclePartSlot.ENERGY_RECOVERY_UNIT;
    }
}
