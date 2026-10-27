package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.sound.VehicleShortBlockPcmStream;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class EnginePcmChannelMixin {
    @Shadow
    @Final
    private int source;
    @Shadow
    private AudioStream stream;
    @Shadow
    private int streamingBufferSize;

    @Shadow
    private void pumpBuffers(int count) {
        throw new AssertionError();
    }

    @Unique
    private boolean polaris$explicitStop;

    @Inject(method = "attachBufferStream", at = @At("HEAD"), cancellable = true)
    private void polaris$attachEngineStream(AudioStream incoming, CallbackInfo ci) {
        if (!(incoming instanceof VehicleShortBlockPcmStream shortBlocks)) return;
        stream = incoming;
        streamingBufferSize = Math.round(incoming.getFormat().getSampleRate() * shortBlocks.blockSeconds()) * 2;
        pumpBuffers(shortBlocks.queuedBlocks());
        ci.cancel();
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void polaris$explicitStop(CallbackInfo ci) {
        polaris$explicitStop = true;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void polaris$explicitPlay(CallbackInfo ci) {
        polaris$explicitStop = false;
    }

    @Inject(method = "updateStream", at = @At("TAIL"))
    private void polaris$recoverUnderrun(CallbackInfo ci) {
        if (stream instanceof VehicleShortBlockPcmStream && !polaris$explicitStop
                && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED
                && AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) > 0) {
            AL10.alSourcePlay(source);
        }
    }
}
