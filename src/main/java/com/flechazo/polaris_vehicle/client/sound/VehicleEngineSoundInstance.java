package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePerformance;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePerformanceCalculator;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

final class VehicleEngineSoundInstance extends AbstractTickableSoundInstance implements VehicleEnginePcmSound {
    static final float OUTPUT_GAIN = 0.5F; // Overall engine loudness after the per-profile mix (0.5 = -6 dB).
    private final AbstractVehicleEntity vehicle;
    private final VehicleEngineSoundMix mix;
    private final int layer;
    private final VehicleEngineSoundProfile profile;

    VehicleEngineSoundInstance(
            AbstractVehicleEntity vehicle,
            VehicleEngineSoundProfile profile,
            VehicleEngineSoundMix mix,
            int layer
    ) {
        super(
                SoundEvent.createVariableRangeEvent(profile.layered() ? profile.mixedSoundEvent() : profile.loopSoundEvent(layer)),
                SoundSource.NEUTRAL,
                SoundInstance.createUnseededRandom()
        );
        this.vehicle = vehicle;
        this.mix = mix;
        this.layer = layer;
        this.profile = profile;
        looping = true;
        delay = 0;
        volume = 0.0F;
        pitch = profile.layered() ? 1 : mix.pitch(layer);
        attenuation = Attenuation.LINEAR;
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean usesEnginePcmStream() {
        return profile.layered();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(
            SoundBufferLibrary buffers,
            Sound sound, boolean looping) {
        if (!profile.layered()) return super.getStream(buffers, sound, looping);
        return VehicleEnginePcmBank.INSTANCE.get(profile).<AudioStream>thenApply(bank -> new VehicleEnginePcmStream(bank, mix))
                .whenComplete((stream, failure) -> {
                    if (failure != null) {
                        finish();
                    }
                });
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || !vehicle.isEngineRunning()) {
            stop();
            return;
        }

        updatePosition();
        long gameTick = vehicle.level().getGameTime();
        if (mix.needsUpdate(gameTick)) {
            VehiclePerformance performance = VehiclePerformanceCalculator.calculate(
                    vehicle.definition(),
                    vehicle.vehicleData()
            );
            double signedSpeed = vehicle.getDeltaMovement().dot(vehicle.horizontalForwardDirection()) * 20.0;
            mix.updateDriving(gameTick, signedSpeed, performance.maxSpeedMetersPerSecond(),
                    performance.reverseSpeedMetersPerSecond(), vehicle.throttleInput(), vehicle.brakeInput());
        }
        // PCM already contains engine pitch/gain. MC still applies category/master
        // volume, positional attenuation and pause to this single output channel.
        pitch = profile.layered() ? 1 : mix.pitch(layer);
        volume = (profile.layered() ? 1 : mix.volume(layer)) * OUTPUT_GAIN;
    }

    void finish() {
        stop();
    }

    private void updatePosition() {
        var position = VehicleEngineSoundPosition.world(vehicle);
        x = position.x;
        y = position.y;
        z = position.z;
    }

}
