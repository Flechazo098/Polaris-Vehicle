package com.flechazo.polaris_vehicle.vehicle.physics;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.part.*;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.data.VehiclePowertrainParameters;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;

import java.util.List;
import java.util.stream.IntStream;

public final class VehiclePerformanceCalculator {
    static final double RACING_TIRE_GRASS_SPEED_MULTIPLIER = 0.70;
    static final double RACING_TIRE_SAND_SPEED_MULTIPLIER = 0.40;
    static final double RACING_TIRE_GRAVEL_SPEED_MULTIPLIER = 0.60;

    public static VehiclePerformance calculate(VehicleDefinition definition, VehicleInstanceData data) {
        return calculate(definition, data, VehicleWheelTerrain.normal(definition.wheelSlots().size()));
    }

    public static VehiclePerformance calculate(
            VehicleDefinition definition,
            VehicleInstanceData data,
            VehicleWheelTerrain terrain
    ) {
        return calculate(definition, data, terrain, 0.0);
    }

    private static final double SLIPSTREAM_ACCELERATION_PER_BONUS = 0.35;

    public static VehiclePerformance calculate(
            VehicleDefinition definition,
            VehicleInstanceData data,
            VehicleWheelTerrain terrain,
            double maxSpeedBonus
    ) {
        Maybe<InstalledPart> engine = data.part(VehiclePartSlot.ENGINE)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .filter(VehicleEngineDefinitions::isKnownEngine);
        boolean usableEngine = engine.isDefined();
        boolean driveableWheels = data.hasDriveableWheels(definition);
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(definition.id());
        VehiclePowertrainParameters tuning = VehicleTypeParameters.resolve(definition.id()).powertrain();
        if (terrain.wheels().size() != profile.wheels().size()) {
            throw new IllegalArgumentException("Wheel terrain must align with the vehicle physics profile");
        }

        double horsepower = engine.fold(() -> 0.0, VehiclePerformanceCalculator::engineHorsepower)
                * tuning.engineHorsepowerMultiplier();
        double wheelSpeed = IntStream.range(0, profile.wheels().size())
                .mapToDouble(index -> data.part(profile.wheels().get(index).slot())
                        .filter(InstalledPart::enabled)
                        .fold(() -> 0.0, wheel -> wheelSpeedModifier(wheel, terrain.at(index))))
                .sum();
        double maxSpeed = horsepower - tuning.bodyWeightPenalty()
                + wheelSpeed
                + tuning.baseMaxSpeedModifier();
        double acceleration = accelerationBreakdown(definition, data, terrain).total();

        double[] fuelModifiers = fuelModifiers(definition, data.fuel());
        maxSpeed += fuelModifiers[0];

        Maybe<VehiclePowerDefinition> extraPower = data.part(VehiclePartSlot.EXTRA_POWER_UNIT)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(definitionValue -> definitionValue.matches(part)));
        double extraPowerSpeed = extraPower.fold(
                () -> 0.0,
                power -> power.normalMaxSpeedModifier()
                        + (data.boostRemainingTicks() > 0 ? power.boostMaxSpeedModifier() : 0.0)
        );
        maxSpeed += extraPowerSpeed;

        Maybe<VehiclePowerDefinition> recovery = data.part(VehiclePartSlot.ENERGY_RECOVERY_UNIT)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(definitionValue -> definitionValue.matches(part)));
        maxSpeed += recovery.fold(() -> 0.0, VehiclePowerDefinition::normalMaxSpeedModifier);
        maxSpeed += engine.flatMap(part -> VehicleEngineDefinitions.find(part.partId()))
                .fold(() -> 0.0, VehicleEngineDefinition::maximumSpeedModifier);

        maxSpeed = Math.max(0, maxSpeed + Math.max(0.0, maxSpeedBonus));
        // The wake cuts drag, which also frees thrust below the top speed:
        // without it the tow did nothing until the follower was already flat out.
        acceleration += Math.max(0.0, maxSpeedBonus) * SLIPSTREAM_ACCELERATION_PER_BONUS;
        maxSpeed *= racingTireTerrainSpeedMultiplier(profile, data, terrain);
        if (!usableEngine) {
            maxSpeed = 0;
            acceleration = 0;
        }

        boolean canDrive = data.engineRunning()
                && data.fuel() > 0
                && usableEngine
                && driveableWheels;
        return new VehiclePerformance(
                usableEngine ? Math.max(1.0, maxSpeed) : 0.0,
                Math.max(0.0, acceleration),
                Math.clamp(
                        maxSpeed * tuning.reverseSpeedRatio(),
                        tuning.minimumReverseSpeed(),
                        tuning.maximumReverseSpeed()
                ),
                tuning.brakingMetersPerSecondSquared(),
                canDrive,
                engine.flatMap(part -> VehicleEngineDefinitions.find(part.partId()))
                        .fold(() -> 3.0, VehicleEngineDefinition::accelerationRampSeconds)
        );
    }

    public static boolean isTypeB(InstalledPart part) {
        return part.partId().equals(VehiclePowerDefinitions.EXTRA_B.id());
    }

    public record AccelerationBreakdown(double engine, double tuning, double tires, double fuel,
                                        double power, double baseAdjustment) {
        public double total() {
            return Math.max(0, engine + tuning + tires + fuel + power + baseAdjustment);
        }
    }

    public static AccelerationBreakdown accelerationBreakdown(VehicleDefinition definition,
                                                              VehicleInstanceData data, VehicleWheelTerrain terrain) {
        var engine = data.part(VehiclePartSlot.ENGINE).filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped()).filter(VehicleEngineDefinitions::isKnownEngine);
        if (engine.isEmpty()) return new AccelerationBreakdown(0, 0, 0, 0, 0, 0);
        var tuning = VehicleTypeParameters.resolve(definition.id()).powertrain();
        var profile = VehiclePhysicsProfile.require(definition.id());
        double rawEngine = engine.fold(() -> 0.0, VehiclePerformanceCalculator::engineAcceleration);
        double tires = IntStream.range(0, profile.wheels().size())
                .mapToDouble(index -> data.part(profile.wheels().get(index).slot()).filter(InstalledPart::enabled)
                        .fold(() -> 0.0, wheel -> wheelAccelerationModifier(wheel, terrain.at(index)))).sum();
        double power = 0;
        for (var slot : List.of(VehiclePartSlot.EXTRA_POWER_UNIT, VehiclePartSlot.ENERGY_RECOVERY_UNIT)) {
            power += data.part(slot).filter(InstalledPart::enabled).filter(part -> !part.isScrapped())
                    .flatMap(part -> VehiclePowerDefinitions.find(part.partId()).filter(value -> value.matches(part)))
                    .fold(() -> 0.0, value -> value.normalAccelerationModifier()
                            + (slot == VehiclePartSlot.EXTRA_POWER_UNIT && data.boostRemainingTicks() > 0
                            ? value.boostAccelerationModifier() : 0));
        }
        double tuningBonus = rawEngine * (tuning.engineAccelerationMultiplier() - 1);
        double fuel = fuelModifiers(definition, data.fuel())[1];
        double baseAdjustment = tuning.baseAccelerationModifier();
        double beforeFloor = rawEngine + tuningBonus + tires + fuel + power + baseAdjustment;
        baseAdjustment += Math.max(0, 0.5 - beforeFloor);
        return new AccelerationBreakdown(rawEngine, tuningBonus, tires, fuel, power, baseAdjustment);
    }

    private static double engineHorsepower(InstalledPart engine) {
        return VehicleEngineDefinitions.find(engine.partId())
                .filter(definition -> definition.matches(engine))
                .fold(() -> 0.0, definition -> definition.effectiveHorsepower(engine));
    }

    private static double engineAcceleration(InstalledPart engine) {
        return VehicleEngineDefinitions.find(engine.partId())
                .filter(definition -> definition.matches(engine))
                .fold(() -> 0.0, definition -> definition.effectiveAcceleration(engine));
    }

    static double wheelSpeedModifier(InstalledPart wheel, WheelTerrain terrain) {
        return VehicleTireDefinitions.find(wheel.partId())
                .filter(definition -> definition.matches(wheel))
                .fold(() -> 0.0, definition -> definition.speedModifier(wheel, terrain));
    }

    static double wheelAccelerationModifier(InstalledPart wheel, WheelTerrain terrain) {
        return VehicleTireDefinitions.find(wheel.partId())
                .filter(definition -> definition.matches(wheel))
                .fold(() -> 0.0, definition -> definition.accelerationModifier(wheel, terrain));
    }

    static double racingTireTerrainSpeedMultiplier(
            VehiclePhysicsProfile profile,
            VehicleInstanceData data,
            VehicleWheelTerrain terrain
    ) {
        double multiplier = 1.0;
        for (int index = 0; index < profile.wheels().size(); index++) {
            boolean racingTire = data.part(profile.wheels().get(index).slot())
                    .filter(InstalledPart::enabled)
                    .filter(InstalledPart::isFullyTightened)
                    .filter(VehiclePerformanceCalculator::isRacingTire)
                    .isDefined();
            if (!racingTire) {
                continue;
            }
            if (terrain.at(index) == WheelTerrain.SAND) {
                return RACING_TIRE_SAND_SPEED_MULTIPLIER;
            }
            if (terrain.at(index) == WheelTerrain.GRAVEL) {
                multiplier = Math.min(multiplier, RACING_TIRE_GRAVEL_SPEED_MULTIPLIER);
            }
            if (terrain.at(index) == WheelTerrain.GRASS || terrain.at(index) == WheelTerrain.SOFT_GRASS) {
                multiplier = Math.min(multiplier, RACING_TIRE_GRASS_SPEED_MULTIPLIER);
            }
        }
        return multiplier;
    }

    private static boolean isRacingTire(InstalledPart wheel) {
        return wheel.partId().equals(VehicleTireDefinitions.RACING_SPEED.id())
                || wheel.partId().equals(VehicleTireDefinitions.RACING_BALANCED.id())
                || wheel.partId().equals(VehicleTireDefinitions.RACING_ENDURANCE.id());
    }

    private static double[] fuelModifiers(VehicleDefinition definition, int fuel) {
        if (fuel <= 0 || definition.fuelCapacity() <= 0) {
            return new double[]{0.0, 0.0};
        }
        double ratio = (double) fuel / definition.fuelCapacity();
        if (ratio >= 0.9) {
            return new double[]{-1.0, -0.5};
        }
        if (ratio >= 0.7) {
            return new double[]{0.0, 0.0};
        }
        if (ratio >= 0.5) {
            return new double[]{1.0, 0.5};
        }
        if (ratio >= 0.2) {
            return new double[]{2.0, 1.0};
        }
        return new double[]{3.0, 1.5};
    }

}
