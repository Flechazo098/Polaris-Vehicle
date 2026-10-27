package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.sound.ElectricMotorSoundFilter;
import com.flechazo.polaris_vehicle.client.sound.VehicleFilteredSoundChannel;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ElectricSoundChannelMixin implements VehicleFilteredSoundChannel {
    @Shadow
    @Final
    private int source;
    @Unique
    private ElectricMotorSoundFilter polaris_vehicle$electricFilter;

    @Override
    public void polaris_vehicle$enableElectricFiltering(float initialPitch) {
        if (polaris_vehicle$electricFilter == null) {
            polaris_vehicle$electricFilter = ElectricMotorSoundFilter.create(source, initialPitch);
        }
    }

    @Inject(method = "setPitch", at = @At("RETURN"))
    private void polaris_vehicle$updateElectricFilter(float pitch, CallbackInfo ci) {
        if (polaris_vehicle$electricFilter != null) polaris_vehicle$electricFilter.update(pitch);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void polaris_vehicle$releaseElectricFilter(CallbackInfo ci) {
        if (polaris_vehicle$electricFilter != null) {
            polaris_vehicle$electricFilter.close();
            polaris_vehicle$electricFilter = null;
        }
    }
}
