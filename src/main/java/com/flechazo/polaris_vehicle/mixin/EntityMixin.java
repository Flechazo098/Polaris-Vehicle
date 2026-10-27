package com.flechazo.polaris_vehicle.mixin;

import com.flechazo.polaris_vehicle.content.block.RampShape;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleEntityCollisionResolver;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private static float[] collectCandidateStepUpHeights(
            AABB box, List<VoxelShape> colliders, float maximum, float skipped
    ) {
        throw new AssertionError();
    }

    @Redirect(method = "collide", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;collectCandidateStepUpHeights(Lnet/minecraft/world/phys/AABB;Ljava/util/List;FF)[F"))
    private float[] polarisVehicle$exactRampStepHeight(
            AABB box, List<VoxelShape> colliders, float maximum, float skipped,
            Vec3 movement
    ) {
        if (colliders.stream().noneMatch(RampShape.class::isInstance)) {
            return collectCandidateStepUpHeights(box, colliders, maximum, skipped);
        }
        List<VoxelShape> ordinary = colliders.stream()
                .filter(shape -> !(shape instanceof RampShape)).toList();
        FloatArraySet heights = new FloatArraySet(
                collectCandidateStepUpHeights(box, ordinary, maximum, skipped));
        AABB destination = box.move(movement.x, 0.0, movement.z);
        for (VoxelShape shape : colliders) {
            if (shape instanceof RampShape ramp) {
                float height = (float) (ramp.supportHeight(destination) - box.minY);
                if (height >= 0.0F && height <= maximum && height != skipped) {
                    heights.add(height);
                }
            }
        }
        float[] result = heights.toFloatArray();
        FloatArrays.unstableSort(result);
        return result;
    }

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void polarisVehicle$collideFullStructure(
            Vec3 requestedMovement,
            CallbackInfoReturnable<Vec3> callback
    ) {
        if ((Object) this instanceof AbstractVehicleEntity vehicle) {
            vehicle.resolveStructureMovement(requestedMovement)
                    .ifPresent(callback::setReturnValue);
        }
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void polarisVehicle$collideOrdinaryEntityWithVehicleStructure(
            Vec3 requestedMovement,
            CallbackInfoReturnable<Vec3> callback
    ) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof AbstractVehicleEntity) {
            return;
        }
        callback.setReturnValue(VehicleEntityCollisionResolver.resolve(
                entity,
                callback.getReturnValue()
        ));
    }
}
