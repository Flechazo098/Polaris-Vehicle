package com.flechazo.polaris_vehicle.content.entity;

import com.flechazo.polaris_vehicle.item.CrewDollVariant;
import com.flechazo.polaris_vehicle.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
public final class CrewDollEntity extends Entity implements GeoEntity {
    private static final String VARIANT_TAG = "Variant";
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(
            CrewDollEntity.class,
            EntityDataSerializers.INT
    );

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public CrewDollEntity(EntityType<? extends CrewDollEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public CrewDollVariant variant() {
        return CrewDollVariant.byId(entityData.get(VARIANT));
    }

    public void setVariant(CrewDollVariant variant) {
        entityData.set(VARIANT, variant.id());
    }

    public ItemStack asItemStack() {
        return new ItemStack(switch (variant()) {
            case FLECHAZO -> ModItems.CREW_DOLL_FLECHAZO.get();
            case NAIMIURA -> ModItems.CREW_DOLL_NAIMIURA.get();
            case BLUE_JUNZI -> ModItems.CREW_DOLL_BLUE_JUNZI.get();
            case ZHIFAN -> ModItems.CREW_DOLL_ZHIFAN.get();
            case DUCKDUCK -> ModItems.CREW_DOLL_DUCKDUCK.get();
            case NEPTUNE_TADASHI -> ModItems.CREW_DOLL_NEPTUNE_TADASHI.get();
            case YELLOW_DUCK -> ModItems.CREW_DOLL_YELLOW_DUCK.get();
            case SDJGE -> ModItems.CREW_DOLL_SDJGE.get();
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, CrewDollVariant.FLECHAZO.id());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setVariant(CrewDollVariant.byId(tag.getInt(VARIANT_TAG)));
        setNoGravity(true);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(VARIANT_TAG, variant().id());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setOldPosAndRot();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved() || isInvulnerableTo(source)) {
            return false;
        }
        if (!(level() instanceof ServerLevel)) {
            return true;
        }
        if (source.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
            return false;
        }
        if (!source.isCreativePlayer()) {
            spawnAtLocation(asItemStack(), 0.1F);
        }
        level().playSound(
                null,
                blockPosition(),
                SoundEvents.ARMOR_STAND_BREAK,
                SoundSource.BLOCKS,
                0.8F,
                1.2F
        );
        discard();
        return true;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "duck_scale", 0,
                state -> variant() == CrewDollVariant.YELLOW_DUCK
                        ? state.setAndContinue(RawAnimation.begin().thenLoop("animation.unknown.scale"))
                        : PlayState.STOP));
    }
}
