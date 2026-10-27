package com.flechazo.polaris_vehicle.mixin;

import com.flechazo.polaris_vehicle.content.block.RampShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public abstract class RampShapeOperationsMixin {
    @Inject(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;"
            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
            + "Lnet/minecraft/world/phys/shapes/BooleanOp;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void polarisVehicle$exactRampIntersection(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (operation != BooleanOp.AND) {
            return;
        }
        RampShape ramp;
        VoxelShape other;
        if (first instanceof RampShape firstRamp && !(second instanceof RampShape)) {
            ramp = firstRamp;
            other = second;
        } else if (second instanceof RampShape secondRamp && !(first instanceof RampShape)) {
            ramp = secondRamp;
            other = first;
        } else {
            return;
        }
        callback.setReturnValue(other.toAabbs().stream().anyMatch(ramp::intersects));
    }
}
