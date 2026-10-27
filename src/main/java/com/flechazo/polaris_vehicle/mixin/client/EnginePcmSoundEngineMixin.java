package com.flechazo.polaris_vehicle.mixin.client;

import com.flechazo.polaris_vehicle.client.sound.VehicleEnginePcmSound;
import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public abstract class EnginePcmSoundEngineMixin {
    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess;createHandle(Lcom/mojang/blaze3d/audio/Library$Pool;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChannelAccess.ChannelHandle> polaris$engineChannelPool(
            ChannelAccess channels, Library.Pool original, SoundInstance sound) {
        return channels.createHandle(sound instanceof VehicleEnginePcmSound engine && engine.usesEnginePcmStream()
                ? Library.Pool.STATIC : original);
    }
}
