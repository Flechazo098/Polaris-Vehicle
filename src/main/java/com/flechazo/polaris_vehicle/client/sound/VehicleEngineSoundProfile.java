package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.util.MathUtil;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.resources.ResourceLocation;

enum VehicleEngineSoundProfile {
    RALLY_CAR("vehicle.engine.rally_car", 5),
    BUS("vehicle.engine.bus", 5),
    SUV("vehicle.engine.suv", 5),
    KART("vehicle.engine.kart", 4),
    FORMULA_CAR("vehicle.engine.formula_car", 5),
    ELECTRIC("vehicle.engine.electric", 1, 0.0F, 6.0F, 12.0F, 0.0F, 0),

    SMALL_CAR("vehicle.engine.small_car", 5, -10.0F, -5.0F, 5.0F, -12.0F, 16),
    SMALL("vehicle.engine.moped", 4, pitchToSemitones(0.72F), pitchToSemitones(1.15F),
            pitchToSemitones(1.58F), pitchToSemitones(0.72F), 0),
    MEDIUM("vehicle.engine.mini_bus", 5, pitchToSemitones(0.70F), pitchToSemitones(1.09F),
            pitchToSemitones(1.48F), pitchToSemitones(0.70F), 0),
    LARGE("vehicle.engine.atv", 5, pitchToSemitones(0.68F), pitchToSemitones(1.05F),
            pitchToSemitones(1.42F), pitchToSemitones(0.68F), 0);

    private final ResourceLocation soundEvent;
    private final int forwardGears;
    private final float idleSemitones;
    private final float middleSemitones;
    private final float redlineSemitones;
    private final float startupSemitones;
    private final int startupTicks;
    private final boolean layered;

    VehicleEngineSoundProfile(String soundEvent, int forwardGears) {
        this(soundEvent, forwardGears, 0, 0, 0, 0, 0, true);
    }

    VehicleEngineSoundProfile(
            String soundEvent,
            int forwardGears,
            float idleSemitones,
            float middleSemitones,
            float redlineSemitones,
            float startupSemitones,
            int startupTicks
    ) {
        this(soundEvent, forwardGears, idleSemitones, middleSemitones, redlineSemitones,
                startupSemitones, startupTicks, false);
    }

    VehicleEngineSoundProfile(String soundEvent, int forwardGears, float idleSemitones,
                              float middleSemitones, float redlineSemitones, float startupSemitones, int startupTicks,
                              boolean layered) {
        this.soundEvent = PolarisVehicle.id(soundEvent
        );
        this.forwardGears = forwardGears;
        this.idleSemitones = idleSemitones;
        this.middleSemitones = middleSemitones;
        this.redlineSemitones = redlineSemitones;
        this.startupSemitones = startupSemitones;
        this.startupTicks = startupTicks;
        this.layered = layered;
    }

    boolean layered() {
        return layered;
    }

    int loopCount() {
        return layered ? 3 : 1;
    }

    ResourceLocation loopSoundEvent(int layer) {
        if (!layered) return soundEvent;
        String suffix = switch (layer) {
            case 0 -> ".low";
            case 1 -> ".mid";
            case 2 -> ".high";
            default -> throw new IllegalArgumentException("Unknown engine sound layer: " + layer);
        };
        return soundEvent.withPath(soundEvent.getPath() + suffix);
    }

    ResourceLocation mixedSoundEvent() {
        return soundEvent.withPath(soundEvent.getPath() + ".mixed");
    }

    float layerPitch(int layer, float rpmRatio, float load) {
        if (!layered) return pitch(rpmRatio);
        float octaves = (Math.clamp(rpmRatio, 0, 1) - VehicleEngineSoundMix.anchor(0)) / .30F;
        float commonRate = (float) Math.pow(2, octaves)
                * semitonesToPitch(-(1 - Math.clamp(load, 0, 1)));
        return commonRate / (1 << layer);
    }

    ResourceLocation soundEvent() {
        return soundEvent;
    }

    int forwardGears() {
        return forwardGears;
    }

    static float electricSemitoneOffset(ResourceLocation vehicleType) {
        if (vehicleType.equals(VehicleDefinitions.TWO_SEAT_CAR_ID)) return -3.0F;
        if (vehicleType.equals(VehicleDefinitions.SMALL_CAR_ID)) return -1.5F;
        return 0.0F;
    }

    float pitch(float rpmRatio) {
        return semitonesToPitch(semitones(rpmRatio));
    }

    float startupPitch(int elapsedTicks) {
        if (startupTicks <= 0) {
            return pitch(VehicleEngineSoundDynamics.IDLE_RPM_RATIO);
        }
        float eased = MathUtil.smoothstep((float) elapsedTicks / startupTicks);
        return semitonesToPitch(lerp(startupSemitones, idleSemitones, eased));
    }

    float startupVolumeRatio(int elapsedTicks) {
        if (startupTicks <= 0) {
            return 1.0F;
        }
        return MathUtil.smoothstep((float) elapsedTicks / startupTicks);
    }

    boolean isStarting(int elapsedTicks) {
        return elapsedTicks < startupTicks;
    }

    private float semitones(float rpmRatio) {
        float normalizedRpm = Math.clamp(
                (rpmRatio - VehicleEngineSoundDynamics.IDLE_RPM_RATIO)
                        / (1.0F - VehicleEngineSoundDynamics.IDLE_RPM_RATIO),
                0.0F,
                1.0F
        );
        if (normalizedRpm <= 0.5F) {
            return lerp(idleSemitones, middleSemitones, normalizedRpm * 2.0F);
        }
        return lerp(middleSemitones, redlineSemitones, (normalizedRpm - 0.5F) * 2.0F);
    }

    static float semitonesToPitch(float semitones) {
        return (float) Math.pow(2.0, semitones / 12.0);
    }

    private static float pitchToSemitones(float pitch) {
        return (float) (12.0 * Math.log(pitch) / Math.log(2.0));
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    static VehicleEngineSoundProfile forVehicle(
            VehicleDefinition vehicle,
            VehiclePartCategory category
    ) {
        if (category == VehiclePartCategory.DRIVE_MOTOR) {
            return ELECTRIC;
        }
        if (vehicle.id().equals(VehicleDefinitions.RALLY_CAR_ID)) return RALLY_CAR;
        if (vehicle.id().equals(VehicleDefinitions.SMALL_BUS_ID)) return BUS;
        if (vehicle.id().equals(VehicleDefinitions.SUV_ID)) return SUV;
        if (vehicle.id().equals(VehicleDefinitions.KART_ID)) return KART;
        if (vehicle.id().equals(VehicleDefinitions.FORMULA_CAR_ID)) return FORMULA_CAR;
        if (vehicle.id().equals(VehicleDefinitions.SMALL_CAR_ID)) {
            return SMALL_CAR;
        }
        return switch (category) {
            case SMALL_ENGINE -> SMALL;
            case MEDIUM_ENGINE, RACING_ENGINE, SUPER_RACING_ENGINE -> MEDIUM;
            case LARGE_ENGINE -> LARGE;
            default -> throw new IllegalArgumentException("Not an engine category: " + category);
        };
    }
}
