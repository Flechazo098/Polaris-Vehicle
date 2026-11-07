package com.flechazo.polaris_vehicle.mixin;

import com.flechazo.polaris_vehicle.api.collision.BlockModelCollisions;
import com.flechazo.polaris_vehicle.api.collision.BlockCollisionGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit namespace registration is the only opt-in. Unregistered block states retain vanilla behavior. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateModelCollisionMixin {
    @Inject(method = {
            "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
    }, at = @At("HEAD"), cancellable = true)
    private void polaris$modelShape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> callback) {
        BlockModelCollisions.find((BlockState) (Object) this).map(BlockCollisionGeometry::shape).ifPresent(callback::setReturnValue);
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void polaris$cachedModelShape(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> callback) {
        BlockModelCollisions.find((BlockState) (Object) this).map(BlockCollisionGeometry::shape).ifPresent(callback::setReturnValue);
    }

    @Inject(method = "hasLargeCollisionShape", at = @At("HEAD"), cancellable = true)
    private void polaris$modelExtendsBlock(CallbackInfoReturnable<Boolean> callback) {
        BlockModelCollisions.find((BlockState) (Object) this).ifPresent(geometry -> callback.setReturnValue(true));
    }

    @Inject(method = "isCollisionShapeFullBlock", at = @At("HEAD"), cancellable = true)
    private void polaris$modelFullBlock(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        BlockModelCollisions.find((BlockState) (Object) this).ifPresent(geometry -> callback.setReturnValue(Block.isShapeFullBlock(geometry.shape())));
    }
}
