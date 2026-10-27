package com.flechazo.polaris_vehicle.vehicle.data;

import cc.sighs.oelib.network.serialization.NetworkSerialization;
import com.flechazo.hkt.Either;
import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.*;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public record VehicleInstanceData(
        int dataVersion,
        ResourceLocation vehicleType,
        ResourceLocation paintId,
        int fuel,
        boolean engineRunning,
        int energy,
        int energyChargeRemainderMilli,
        int boostCooldownTicks,
        int boostRemainingTicks,
        Map<VehiclePartSlot, InstalledPart> parts
) {
    public static final int CURRENT_DATA_VERSION = 2;
    public static final ResourceLocation DEFAULT_PAINT = PolarisVehicle.id("default"
    );

    private static final Codec<Map<VehiclePartSlot, InstalledPart>> PARTS_CODEC = Codec.unboundedMap(
            VehiclePartSlot.CODEC,
            InstalledPart.CODEC
    );

    public static final Codec<VehicleInstanceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("data_version")
                    .forGetter(VehicleInstanceData::dataVersion),
            ResourceLocation.CODEC.fieldOf("vehicle_type").forGetter(VehicleInstanceData::vehicleType),
            ResourceLocation.CODEC.fieldOf("paint_id").forGetter(VehicleInstanceData::paintId),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("fuel").forGetter(VehicleInstanceData::fuel),
            Codec.BOOL.fieldOf("engine_running").forGetter(VehicleInstanceData::engineRunning),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("energy").forGetter(VehicleInstanceData::energy),
            Codec.intRange(0, 999).fieldOf("energy_charge_remainder_milli")
                    .forGetter(VehicleInstanceData::energyChargeRemainderMilli),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("boost_cooldown_ticks")
                    .forGetter(VehicleInstanceData::boostCooldownTicks),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("boost_remaining_ticks")
                    .forGetter(VehicleInstanceData::boostRemainingTicks),
            PARTS_CODEC.fieldOf("parts").forGetter(VehicleInstanceData::parts)
    ).apply(instance, VehicleInstanceData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleInstanceData> STREAM_CODEC =
            NetworkSerialization.autoCodec(VehicleInstanceData.class);

    public static VehicleInstanceData create(VehicleDefinition definition) {
        return new VehicleInstanceData(
                CURRENT_DATA_VERSION,
                definition.id(),
                DEFAULT_PAINT,
                0,
                false,
                0,
                0,
                0,
                0,
                Map.of()
        );
    }

    public VehicleInstanceData sanitized(VehicleDefinition definition) {
        int clampedFuel = Math.clamp(fuel, 0, definition.fuelCapacity());
        Map<VehiclePartSlot, InstalledPart> compatibleParts = new LinkedHashMap<>();
        parts.forEach((slot, part) -> {
            if (definition.supports(slot, part.category()) && isKnownPart(part)) {
                compatibleParts.put(slot, VehicleTireDefinitions.applyDurabilityParameters(part));
            }
        });
        boolean canRun = engineRunning
                && clampedFuel > 0
                && hasUsableEngine(compatibleParts);
        int energyCapacity = Maybe.ofNullable(compatibleParts.get(VehiclePartSlot.EXTRA_POWER_UNIT))
                .filter(part -> !part.isScrapped())
                .filter(InstalledPart::enabled)
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(power -> power.matches(part) && power.isExtraPowerUnit()))
                .fold(() -> 0, VehiclePowerDefinition::energyCapacity);
        int sanitizedEnergy = Math.clamp(energy, 0, energyCapacity);
        int sanitizedEnergyRemainder = sanitizedEnergy < energyCapacity
                ? Math.clamp(energyChargeRemainderMilli, 0, 999)
                : 0;
        int sanitizedBoostRemaining = canRun && energyCapacity > 0
                ? Math.max(0, boostRemainingTicks)
                : 0;
        int sanitizedCooldown = energyCapacity > 0 ? Math.max(0, boostCooldownTicks) : 0;
        return new VehicleInstanceData(
                CURRENT_DATA_VERSION,
                definition.id(),
                paintId,
                clampedFuel,
                canRun,
                sanitizedEnergy,
                sanitizedEnergyRemainder,
                sanitizedCooldown,
                sanitizedBoostRemaining,
                compatibleParts
        );
    }

    public Maybe<InstalledPart> part(VehiclePartSlot slot) {
        return Maybe.ofNullable(slot)
                .flatMap(validSlot -> Maybe.ofNullable(parts.get(validSlot)));
    }

    public VehicleInstanceData withFuel(VehicleDefinition definition, int newFuel) {
        return new VehicleInstanceData(
                dataVersion,
                vehicleType,
                paintId,
                Math.clamp(newFuel, 0, definition.fuelCapacity()),
                engineRunning,
                energy,
                energyChargeRemainderMilli,
                boostCooldownTicks,
                boostRemainingTicks,
                parts
        ).sanitized(definition);
    }

    public Either<String, VehicleInstanceData> withPaint(
            VehicleDefinition definition,
            ResourceLocation newPaintId
    ) {
        return Pathway.either(Pathway.nullable(newPaintId).toEither(() -> "Paint id must not be null"))
                .via(paint -> VehiclePaintDefinitions.supports(definition.id(), paint)
                        ? Pathway.<String, ResourceLocation>right(paint)
                        : Pathway.<String, ResourceLocation>left("Vehicle " + definition.id() + " does not support paint " + paint))
                .map(paint -> new VehicleInstanceData(
                        dataVersion,
                        vehicleType,
                        paint,
                        fuel,
                        engineRunning,
                        energy,
                        energyChargeRemainderMilli,
                        boostCooldownTicks,
                        boostRemainingTicks,
                        parts
                ).sanitized(definition))
                .run();
    }

    public VehicleInstanceData withEngineRunning(VehicleDefinition definition, boolean running) {
        return new VehicleInstanceData(
                dataVersion,
                vehicleType,
                paintId,
                fuel,
                running,
                energy,
                energyChargeRemainderMilli,
                boostCooldownTicks,
                boostRemainingTicks,
                parts
        ).sanitized(definition);
    }

    public VehicleInstanceData withEnergy(VehicleDefinition definition, int newEnergy) {
        return new VehicleInstanceData(
                dataVersion,
                vehicleType,
                paintId,
                fuel,
                engineRunning,
                Math.max(0, newEnergy),
                energyChargeRemainderMilli,
                boostCooldownTicks,
                boostRemainingTicks,
                parts
        ).sanitized(definition);
    }

    public VehicleInstanceData withEnergyCharge(
            VehicleDefinition definition,
            int newEnergy,
            int newRemainderMilli
    ) {
        return new VehicleInstanceData(
                dataVersion,
                vehicleType,
                paintId,
                fuel,
                engineRunning,
                Math.max(0, newEnergy),
                Math.clamp(newRemainderMilli, 0, 999),
                boostCooldownTicks,
                boostRemainingTicks,
                parts
        ).sanitized(definition);
    }

    public VehicleInstanceData withBoostState(
            VehicleDefinition definition,
            int newEnergy,
            int newCooldownTicks,
            int newRemainingTicks
    ) {
        return new VehicleInstanceData(
                dataVersion,
                vehicleType,
                paintId,
                fuel,
                engineRunning,
                Math.max(0, newEnergy),
                energyChargeRemainderMilli,
                Math.max(0, newCooldownTicks),
                Math.max(0, newRemainingTicks),
                parts
        ).sanitized(definition);
    }

    public VehicleInstanceData damagePart(
            VehicleDefinition definition,
            VehiclePartSlot slot,
            int amount
    ) {
        if (amount <= 0) {
            return this;
        }
        return part(slot)
                .map(installed -> withPart(
                        definition,
                        slot,
                        installed.withDurability(installed.durability() - amount)
                ).getOrElse(this))
                .orElse(this);
    }

    public VehicleInstanceData repairPart(
            VehicleDefinition definition,
            VehiclePartSlot slot,
            int amount
    ) {
        if (amount <= 0) {
            return this;
        }
        return part(slot)
                .map(installed -> {
                    InstalledPart repaired = installed.repair(amount);
                    return repaired == installed
                            ? this
                            : withPart(definition, slot, repaired).getOrElse(this);
                })
                .orElse(this);
    }

    public boolean hasUsablePart(VehiclePartSlot slot) {
        return part(slot)
                .filter(installed -> !installed.isScrapped())
                .filter(InstalledPart::enabled)
                .isDefined();
    }

    public boolean hasUsableEngine() {
        return part(VehiclePartSlot.ENGINE)
                .filter(engine -> !engine.isScrapped())
                .filter(InstalledPart::enabled)
                .filter(VehicleEngineDefinitions::isKnownEngine)
                .isDefined();
    }

    public boolean hasReadyWheels(VehicleDefinition definition) {
        var wheelSlots = definition.wheelSlots();
        return !wheelSlots.isEmpty() && wheelSlots.stream().allMatch(this::hasReadyWheel);
    }

    public boolean hasDriveableWheels(VehicleDefinition definition) {
        var wheelSlots = definition.wheelSlots();
        return !wheelSlots.isEmpty() && wheelSlots.stream().allMatch(this::hasDriveableWheel);
    }

    public boolean hasScrappedWheel(VehicleDefinition definition) {
        return definition.wheelSlots().stream().anyMatch(slot -> part(slot)
                .filter(VehicleTireDefinitions::isKnownTire)
                .filter(InstalledPart::isScrapped)
                .isDefined());
    }

    public Either<String, VehicleInstanceData> withPart(
            VehicleDefinition definition,
            VehiclePartSlot slot,
            InstalledPart part
    ) {
        return Maybe.ofNullable(definition)
                .toEither("Vehicle definition must not be null")
                .flatMap(validDefinition -> Maybe.ofNullable(slot)
                        .toEither("Vehicle part slot must not be null")
                        .flatMap(validSlot -> Maybe.ofNullable(part)
                                .toEither("Installed part must not be null")
                                .flatMap(validPart -> isKnownPart(validPart)
                                        ? Either.right(validPart)
                                        : Either.left("Unknown or mismatched vehicle part: "
                                                      + validPart.partId()))
                                .flatMap(validPart -> validDefinition.supports(validSlot, validPart.category())
                                        ? Either.right(validPart)
                                        : Either.left("Vehicle " + validDefinition.id()
                                                      + " does not support " + validPart.category()
                                                      + " in " + validSlot))
                                .map(validPart -> {
                                    Map<VehiclePartSlot, InstalledPart> updated = new LinkedHashMap<>(parts);
                                    updated.put(validSlot, validPart);
                                    return new VehicleInstanceData(
                                            dataVersion,
                                            vehicleType,
                                            paintId,
                                            fuel,
                                            engineRunning,
                                            energy,
                                            energyChargeRemainderMilli,
                                            boostCooldownTicks,
                                            boostRemainingTicks,
                                            updated
                                    ).sanitized(validDefinition);
                                })));
    }

    public VehicleInstanceData withoutPart(VehicleDefinition definition, VehiclePartSlot slot) {
        return part(slot).fold(
                () -> this,
                installed -> {
                    Map<VehiclePartSlot, InstalledPart> updated = new LinkedHashMap<>(parts);
                    updated.remove(slot);
                    return new VehicleInstanceData(
                            dataVersion,
                            vehicleType,
                            paintId,
                            fuel,
                            engineRunning,
                            energy,
                            energyChargeRemainderMilli,
                            boostCooldownTicks,
                            boostRemainingTicks,
                            updated
                    )
                            .sanitized(definition);
                }
        );
    }

    private static boolean hasUsableEngine(Map<VehiclePartSlot, InstalledPart> parts) {
        return Maybe.ofNullable(parts.get(VehiclePartSlot.ENGINE))
                .filter(engine -> !engine.isScrapped())
                .filter(InstalledPart::enabled)
                .filter(VehicleEngineDefinitions::isKnownEngine)
                .isDefined();
    }

    private static boolean isKnownPart(InstalledPart part) {
        return switch (part.category()) {
            case WHEEL -> VehicleTireDefinitions.isKnownTire(part);
            case DRIVE_MOTOR, SMALL_ENGINE, MEDIUM_ENGINE, LARGE_ENGINE,
                 RACING_ENGINE, SUPER_RACING_ENGINE -> VehicleEngineDefinitions.isKnownEngine(part);
            case EXTRA_POWER_UNIT, ENERGY_RECOVERY_UNIT -> VehiclePowerDefinitions.isKnownPowerUnit(part);
        };
    }

    private boolean hasReadyWheel(VehiclePartSlot slot) {
        return part(slot)
                .filter(VehicleTireDefinitions::isKnownTire)
                .filter(wheel -> !wheel.isScrapped())
                .filter(InstalledPart::enabled)
                .filter(InstalledPart::isFullyTightened)
                .isDefined();
    }

    private boolean hasDriveableWheel(VehiclePartSlot slot) {
        return part(slot)
                .filter(VehicleTireDefinitions::isKnownTire)
                .filter(InstalledPart::enabled)
                .filter(InstalledPart::isFullyTightened)
                .isDefined();
    }
}
