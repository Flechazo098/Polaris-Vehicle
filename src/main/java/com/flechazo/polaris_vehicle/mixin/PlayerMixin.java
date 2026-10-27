package com.flechazo.polaris_vehicle.mixin;

import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void polarisVehicle$doubleSneakToDismount(CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (player.getVehicle() instanceof AbstractVehicleEntity) {
            callback.setReturnValue(false);
        }
    }
}
