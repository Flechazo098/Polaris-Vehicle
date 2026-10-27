package com.flechazo.polaris_vehicle.client.sound;
final class VehicleEngineSoundMix {
    private static final float LOW_RPM = 0.30F, MID_RPM = 0.60F, HIGH_RPM = 0.90F;
    private static final float LOAD_RESPONSE = 0.25F;
    private static final float VOLUME_RESPONSE = 0.16F;
    private static final float PITCH_RESPONSE = 0.20F;
    private static final float ELECTRIC_RELEASE_RESPONSE = 0.40F;
    private static final float ELECTRIC_ATTACK_RESPONSE = 0.12F;
    private static final float ELECTRIC_PITCH_RISE_PER_TICK = 0.35F;
    private static final float ELECTRIC_PITCH_FALL_PER_TICK = 0.60F;
    private final VehicleEngineSoundProfile profile;
    private final VehicleEngineSoundDynamics dynamics;
    private final VehicleEngineSoundDriveState.Transition driveTransition = new VehicleEngineSoundDriveState.Transition();
    private final float[] pitches;
    private final float[] gains;
    private final float electricBasePitch;
    private long sampledTick = Long.MIN_VALUE;
    private int elapsedTicks;
    private float volume, load, electricSemitones;
    private volatile EnginePcmMixer.Control pcmControl = new EnginePcmMixer.Control(1, 0, 0);

    VehicleEngineSoundMix(VehicleEngineSoundProfile profile) {
        this(profile, 0.0F);
    }

    VehicleEngineSoundMix(VehicleEngineSoundProfile profile, float electricSemitoneOffset) {
        this.profile = profile;
        electricBasePitch = VehicleEngineSoundProfile.semitonesToPitch(
                Math.clamp(electricSemitoneOffset, -3.0F, 0.0F));
        dynamics = new VehicleEngineSoundDynamics(profile.forwardGears());
        pitches = new float[profile.loopCount()];
        gains = new float[profile.loopCount()];
        for (int layer = 0; layer < pitches.length; layer++) {
            pitches[layer] = profile.layered()
                    ? profile.layerPitch(layer, VehicleEngineSoundDynamics.IDLE_RPM_RATIO, 0)
                    : profile.startupPitch(0);
        }
        if (profile == VehicleEngineSoundProfile.ELECTRIC) pitches[0] = electricBasePitch;
    }

    void update(long gameTick, double speedRatio, float throttle) {
        updateResolved(gameTick, speedRatio, throttle, false, Math.abs(speedRatio) * 30);
    }

    void updateDriving(long gameTick, double signedSpeed, double forwardLimit, double reverseLimit,
                       float requestedThrottle, boolean explicitBrake) {
        if (!needsUpdate(gameTick)) return;
        if (profile == VehicleEngineSoundProfile.ELECTRIC) {
            // The same physical speed must sound identical in either direction.
            // Do not normalize reverse by its lower speed limit or rev at rest.
            updateResolved(gameTick, Math.abs(signedSpeed) / Math.max(forwardLimit, 1.0),
                    requestedThrottle, signedSpeed < 0, Math.abs(signedSpeed));
            return;
        }
        var drive = driveTransition.update(signedSpeed, forwardLimit, reverseLimit,
                requestedThrottle, explicitBrake);
        updateResolved(gameTick, drive.speedRatio(), drive.driveThrottle(), drive.reverse(), Math.abs(signedSpeed));
    }

    private void updateResolved(long gameTick, double speedRatio, float driveThrottle, boolean reverse,
                                double movementSpeed) {
        if (sampledTick == gameTick) return;
        sampledTick = gameTick;
        if (profile == VehicleEngineSoundProfile.ELECTRIC) {
            updateElectric(speedRatio, movementSpeed);
            return;
        }
        var output = dynamics.update(speedRatio, driveThrottle, reverse);
        load = approach(load, output.load(), LOAD_RESPONSE);
        float targetVolume = 0.30F + 0.34F * (profile.layered() ? load : output.load())
                + 0.18F * output.rpmRatio();
        elapsedTicks++;
        if (profile.isStarting(elapsedTicks)) {
            volume = targetVolume * profile.startupVolumeRatio(elapsedTicks);
            pitches[0] = profile.startupPitch(elapsedTicks);
        } else {
            volume = approach(volume, targetVolume, VOLUME_RESPONSE);
            if (profile.layered()) {
                // Smooth one transport, then derive all read rates from it. Independent
                // smoothing/clamping of 1x/2x/4x voices destroys their common frequency.
                pitches[0] = approach(pitches[0], profile.layerPitch(0, output.rpmRatio(), load), .45F);
                for (int layer = 1; layer < pitches.length; layer++) pitches[layer] = pitches[0] / (1 << layer);
            } else {
                pitches[0] = approach(pitches[0], profile.layerPitch(0, output.rpmRatio(), load), PITCH_RESPONSE);
            }
        }
        // Crossfade a common smoothed RPM, this keeps their summed power stable during gear changes.
        float audibleRpm = profile.layered()
                ? LOW_RPM + .30F * (float) (Math.log(pitches[0]) / Math.log(2)) : output.rpmRatio();
        for (int layer = 0; layer < gains.length; layer++) {
            gains[layer] = volume * (profile.layered() ? weight(layer, audibleRpm) : 1.0F);
        }
        if (profile.layered()) pcmControl = new EnginePcmMixer.Control(pitches[0],
                Math.clamp((audibleRpm - LOW_RPM) / .30F, 0, 2), volume);
    }

    private void updateElectric(double speedRatio, double movementSpeed) {
        float speed = (float) Math.clamp(Math.abs(speedRatio), 0.0, 1.0);
        // Ignore suspension drift; fade into the 20% starting gain over a tiny physical speed range.
        float moving = (float) Math.clamp((movementSpeed - .05) / .10, 0.0, 1.0);
        moving = moving * moving * (3.0F - 2.0F * moving);
        // Reserve the louder part of the authored loop for faster travel.
        float targetVolume = moving * (0.20F + 0.80F * (float) Math.pow(speed, 1.5));
        volume = approach(volume, targetVolume,
                targetVolume < volume ? ELECTRIC_RELEASE_RESPONSE : ELECTRIC_ATTACK_RESPONSE);
        // OpenAL pitch changes playback rate. 2x is +12 semitones, and also MC's
        // maximum pitch. A fixed reduction gives each motor vehicle its timbre.
        // Smooth in perceptual semitones, with a slew limit for sudden speed
        // corrections. A linear playback multiplier rose too quickly at low speed.
        float targetSemitones = 12.0F * speed;
        float change = (targetSemitones - electricSemitones) * PITCH_RESPONSE;
        electricSemitones += Math.clamp(change,
                -ELECTRIC_PITCH_FALL_PER_TICK, ELECTRIC_PITCH_RISE_PER_TICK);
        if (Math.abs(targetSemitones - electricSemitones) < 1E-4F) electricSemitones = targetSemitones;
        pitches[0] = Math.clamp(electricBasePitch * VehicleEngineSoundProfile.semitonesToPitch(electricSemitones), .5F, 2.0F);
        gains[0] = volume;
    }

    float pitch(int layer) {
        return pitches[layer];
    }

    float volume(int layer) {
        return gains[layer];
    }

    EnginePcmMixer.Control pcmControl() {
        return pcmControl;
    }

    boolean needsUpdate(long gameTick) {
        return sampledTick != gameTick;
    }

    static float anchor(int layer) {
        return switch (layer) {
            case 0 -> LOW_RPM;
            case 1 -> MID_RPM;
            case 2 -> HIGH_RPM;
            default -> throw new IllegalArgumentException("Unknown engine sound layer: " + layer);
        };
    }

    /**
     * Equal-power crossfade: at most two adjacent recordings are audible.
     */
    static float weight(int layer, float rpm) {
        float blend = Math.clamp((rpm - LOW_RPM) / (MID_RPM - LOW_RPM), 0.0F, 1.0F);
        if (rpm > MID_RPM) {
            blend = Math.clamp((rpm - MID_RPM) / (HIGH_RPM - MID_RPM), 0.0F, 1.0F);
            return switch (layer) {
                case 0 -> 0;
                case 1 -> (float) Math.cos(blend * Math.PI / 2.0);
                case 2 -> (float) Math.sin(blend * Math.PI / 2.0);
                default -> throw new IllegalArgumentException("Unknown engine sound layer: " + layer);
            };
        }
        return switch (layer) {
            case 0 -> (float) Math.cos(blend * Math.PI / 2.0);
            case 1 -> (float) Math.sin(blend * Math.PI / 2.0);
            case 2 -> 0;
            default -> throw new IllegalArgumentException("Unknown engine sound layer: " + layer);
        };
    }

    private static float approach(float current, float target, float response) {
        float value = current + (target - current) * response;
        return Math.abs(value - target) < 1.0E-4F ? target : value;
    }
}
