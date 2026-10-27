package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.model.VehicleTireSteeringLimits;
import com.flechazo.polaris_vehicle.config.VehicleGameplayConfig;
import com.flechazo.polaris_vehicle.content.block.AssemblyTableBlock;
import com.flechazo.polaris_vehicle.content.block.entity.AssemblyTableBlockEntity;
import com.flechazo.polaris_vehicle.fuel.VehicleFuelService;
import com.flechazo.polaris_vehicle.fuel.VehicleFuelTransferResult;
import com.flechazo.polaris_vehicle.item.*;
import com.flechazo.polaris_vehicle.network.VehicleActionRateLimit;
import com.flechazo.polaris_vehicle.network.VehicleDataSyncPacket;
import com.flechazo.polaris_vehicle.network.VehicleMaintenanceTransformPacket;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartCategory;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.power.VehicleEnergyChargeResult;
import com.flechazo.polaris_vehicle.power.VehicleEnergyService;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.registry.ModItemTags;
import com.flechazo.polaris_vehicle.registry.ModItems;
import com.flechazo.polaris_vehicle.registry.ModSounds;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.util.MathUtil;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleBlockCollisionResolver;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionRole;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.data.*;
import com.flechazo.polaris_vehicle.vehicle.physics.*;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.*;
import com.flechazo.polaris_vehicle.vehicle.seat.VehicleSeatSelector;
import com.flechazo.polaris_vehicle.vehicle.seat.VehicleSeatTransform;
import com.flechazo.polaris_vehicle.vehicle.seat.VehicleWheelInteractionRegions;
import com.flechazo.polaris_vehicle.vehicle.seat.VehicleWheelSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;
import java.util.function.UnaryOperator;

public abstract class AbstractVehicleEntity extends Entity implements GeoEntity {
    private final VehiclePhysicsInputCache physicsInputCache =
            new VehiclePhysicsInputCache();
    private VehicleWheelTerrain cachedParkedTerrain;
    private static final String SAVE_KEY = "PolarisVehicleData";
    private static final String MAINTENANCE_STATE_KEY = "MaintenanceState";
    private static final String ASSEMBLY_TABLE_POS_KEY = "AssemblyTablePos";
    private static final String ASSEMBLY_OWNER_KEY = "AssemblyOwner";
    private static final String CARRIER_KEY = "Carrier";
    private static final String JACK_ANCHOR_X_KEY = "JackAnchorX";
    private static final String JACK_ANCHOR_Y_KEY = "JackAnchorY";
    private static final String JACK_ANCHOR_Z_KEY = "JackAnchorZ";
    private static final String JACK_ITEM_STORED_KEY = "JackItemStored";
    private static final String SEAT_ASSIGNMENTS_KEY = "SeatAssignments";
    private static final String VEHICLE_OWNER_UUID_KEY = "VehicleOwnerUuid";
    private static final String VEHICLE_OWNER_NAME_KEY = "VehicleOwnerName";
    private static final double JACK_SUPPORT_HEIGHT = 1.0;
    private static final double CARRY_HAND_TOP_ABOVE_PLAYER = 0.35;
    private static final int PLACEMENT_PROTECTION_TICKS = 60;
    private static final double JACK_LOWER_EPSILON = 1.0E-4;
    private static final double JACK_LOWER_MAX_SHORTFALL = 0.25;
    /*
     * Bullet puts a rigid body to sleep below 0.04 m/s per linear component.
     * A suspension-supported body can therefore report a harmless horizontal
     * residual of sqrt(2) * 0.04 ~= 0.057 m/s while visually at rest. Keep the
     * maintenance dead zone above that physical floor so refuelling, jacking,
     * carrying and dismantling do not reject a settled vehicle. This remains
     * far below even Minecraft's 1.295 m/s sneaking speed.
     */
    private static final double MAINTENANCE_SPEED_EPSILON = 0.10;
    private static final long FUEL_TRANSFER_INTERVAL_TICKS = 20L;
    private static final double FUEL_PORT_TARGET_RADIUS = 0.35;
    private static final int DISMANTLE_REQUIRED_HITS = 6;
    private static final long DISMANTLE_TIMEOUT_TICKS = 60L;
    private static final double DISMANTLE_DISTANCE_SQUARED = 6.0 * 6.0;
    private static final double CLIENT_TELEPORT_DISTANCE_SQUARED = 16.0 * 16.0;
    private static final int CLIENT_SNAPSHOT_MAX_DEPTH = 9;
    private static final int CLIENT_SNAPSHOT_OVERFLOW_RETAINED = 3;
    private static final int STEERING_ANIMATION_TRANSITION_TICKS = 5;
    private static final float SUSPENSION_COMPRESSION_QUANTIZATION = 4096.0F;
    private static final double SLIPSTREAM_SEARCH_BODY_MARGIN = 6.0;
    private static final double SLIPSTREAM_LONGEST_LEADER = 14.0;

    private static final EntityDataAccessor<String> VEHICLE_TYPE = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> ENGINE_RUNNING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Integer> ENERGY = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> STEERING_INPUT = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Float> ACTUAL_STEERING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class, EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Integer> THROTTLE_INPUT = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> BRAKE_INPUT = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> HORN_ACTIVE = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Float> PRECISE_YAW = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Quaternionf> BODY_ROTATION = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.QUATERNION
    );
    private static final EntityDataAccessor<Integer> MAINTENANCE_STATE = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Long> JACK_LIFT_START_TICK = SynchedEntityData.defineId(
            AbstractVehicleEntity.class, EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Boolean> JACK_LOWERING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Integer> CARRIER_ENTITY_ID = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Boolean> FRONT_LEFT_WHEEL_INSTALLED = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> FRONT_RIGHT_WHEEL_INSTALLED = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> FRONT_CENTER_WHEEL_INSTALLED = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> REAR_LEFT_WHEEL_INSTALLED = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> REAR_RIGHT_WHEEL_INSTALLED = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Integer> FRONT_LEFT_WHEEL_TIGHTENING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FRONT_RIGHT_WHEEL_TIGHTENING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> FRONT_CENTER_WHEEL_TIGHTENING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> REAR_LEFT_WHEEL_TIGHTENING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Integer> REAR_RIGHT_WHEEL_TIGHTENING = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.INT
    );
    private static final EntityDataAccessor<Float> SUSPENSION_COMPRESSION_0 = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SUSPENSION_COMPRESSION_1 = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SUSPENSION_COMPRESSION_2 = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> SUSPENSION_COMPRESSION_3 = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> JACK_ANCHOR_PRESENT = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Long> JACK_ANCHOR_X_BITS = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Long> JACK_ANCHOR_Y_BITS = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<Long> JACK_ANCHOR_Z_BITS = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.LONG
    );
    private static final EntityDataAccessor<CompoundTag> SEAT_ASSIGNMENTS = SynchedEntityData.defineId(
            AbstractVehicleEntity.class,
            EntityDataSerializers.COMPOUND_TAG
    );

    private final VehicleDefinition defaultDefinition;
    /*
     * The chassis, controls and hot-swappable tires are three independent
     * GeckoLib model passes for the same entity. An entity's default
     * InstancedAnimatableInstanceCache ignores the renderer instance id and
     * returns one manager, so the chassis marks the frame as updated before
     * the component models run. After their first frame, GeoModel then treats
     * every component pass as a duplicate and skips setCustomAnimations.
     *
     * Use GeckoLib's keyed cache so the renderer ids (entity id for chassis,
     * shifted ids for controls/tires) actually select independent managers.
     */
    private final AnimatableInstanceCache animationCache =
            GeckoLibUtil.createInstanceCache(this, true);
    private final RawAnimation turnLeftAnimation;
    private final RawAnimation turnRightAnimation;
    private VehicleInstanceData vehicleData;
    private VehicleSeatAssignments seatAssignments = VehicleSeatAssignments.empty();
    private VehicleMaintenanceState maintenanceState = VehicleMaintenanceState.NORMAL;
    private Maybe<Vec3> jackAnchor = Maybe.none();
    private boolean jackItemStored;
    private UUID jackLoweringPlayer;
    private long jackLoweringEndTick;
    private BlockPos assemblyTablePos;
    private UUID assemblyOwnerUuid;
    private int assemblyValidationGraceTicks;
    private UUID carrierUuid;
    private int placementProtectionTicks;
    private UUID vehicleOwnerUuid;
    private String vehicleOwnerName = "";
    private UUID fuelingPlayerUuid;
    private final VehicleDismountGesture dismountGesture = new VehicleDismountGesture();
    private InteractionHand fuelingHand;
    private long nextFuelTransferGameTime;
    private UUID dismantlingPlayerUuid;
    private int dismantleHits;
    private long dismantleDeadlineGameTime;
    private boolean inputForward;
    private final VehicleThrottleResponse fallbackThrottleResponse = new VehicleThrottleResponse();
    private boolean inputBackward;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputBrake;
    private VehicleSteeringResponse inputSteeringResponse =
            VehicleSteeringResponse.DEFAULT;
    private final VehicleSteeringRack fallbackSteeringRack =
            new VehicleSteeringRack();
    private long lastDriverInputGameTime = -21L;
    private long lastEngineToggleTick = -VehicleActionRateLimit.ONE_SHOT_INTERVAL_TICKS;
    private long lastBoostRequestTick = -VehicleActionRateLimit.ONE_SHOT_INTERVAL_TICKS;
    private Vec3 interpolationTarget = Vec3.ZERO;
    private float interpolationTargetYaw;
    private float interpolationTargetPitch;
    private final ArrayDeque<ClientSnapshot> clientSnapshots = new ArrayDeque<>();
    private final VehicleSlipstreamHold slipstreamHold =
            new VehicleSlipstreamHold();

    private double appliedSlipstreamBonus;

    private double measuredSlipstreamBonus;

    private final VehicleSlipstreamTrail slipstreamTrail = new VehicleSlipstreamTrail();
    private double appliedMaxSpeed;
    private boolean clientSnapshotPlaybackStarted;
    private final VehicleDriverPoseExtrapolator localDriverPose =
            new VehicleDriverPoseExtrapolator();
    private boolean clientSnapshotAwaitingPreciseYaw;
    private boolean clientSnapshotAwaitingBodyRotation;
    private boolean hasClientInterpolationTarget;
    private boolean clientHasPreciseYaw;
    private final Quaternionf clientBodyRotationOld = new Quaternionf();
    private final Quaternionf clientBodyRotation = new Quaternionf();
    private boolean clientTeleportAwaitingBodyRotation;
    private double clientWheelRotationOld;
    private double clientWheelRotation;
    private final VehicleSteeringTimeline clientSteeringTimeline =
            new VehicleSteeringTimeline();
    private final float[] clientSuspensionCompressionOld = new float[4];
    private final float[] clientSuspensionCompression = new float[4];
    private List<Vec3> latestPhysicalWheelContactPoints = List.of();
    private List<Boolean> latestPhysicalWheelContacts = List.of();
    private long latestPhysicalWheelContactGameTime = Long.MIN_VALUE;
    private boolean clientGuiPreview;

    protected AbstractVehicleEntity(
            EntityType<? extends AbstractVehicleEntity> entityType,
            Level level,
            VehicleDefinition defaultDefinition
    ) {
        super(entityType, level);
        this.defaultDefinition = defaultDefinition;
        this.turnLeftAnimation = RawAnimation.begin()
                .thenLoop(VehicleAssets.steeringAnimationName(defaultDefinition.id(), true));
        this.turnRightAnimation = RawAnimation.begin()
                .thenLoop(VehicleAssets.steeringAnimationName(defaultDefinition.id(), false));
        applyVehicleData(DefaultVehicleLoadout.create(defaultDefinition), false);
        blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEHICLE_TYPE, VehicleDefinitions.KART_ID.toString());
        builder.define(FUEL, 0);
        builder.define(ENGINE_RUNNING, false);
        builder.define(ENERGY, 0);
        builder.define(STEERING_INPUT, 0);
        builder.define(ACTUAL_STEERING, 0F);
        builder.define(THROTTLE_INPUT, 0);
        builder.define(BRAKE_INPUT, false);
        builder.define(HORN_ACTIVE, false);
        builder.define(PRECISE_YAW, 0.0F);
        builder.define(BODY_ROTATION, new Quaternionf());
        builder.define(MAINTENANCE_STATE, VehicleMaintenanceState.NORMAL.ordinal());
        builder.define(JACK_LIFT_START_TICK, Long.MIN_VALUE);
        builder.define(JACK_LOWERING, false);
        builder.define(CARRIER_ENTITY_ID, -1);
        builder.define(FRONT_LEFT_WHEEL_INSTALLED, false);
        builder.define(FRONT_RIGHT_WHEEL_INSTALLED, false);
        builder.define(FRONT_CENTER_WHEEL_INSTALLED, false);
        builder.define(REAR_LEFT_WHEEL_INSTALLED, false);
        builder.define(REAR_RIGHT_WHEEL_INSTALLED, false);
        builder.define(FRONT_LEFT_WHEEL_TIGHTENING, 0);
        builder.define(FRONT_RIGHT_WHEEL_TIGHTENING, 0);
        builder.define(FRONT_CENTER_WHEEL_TIGHTENING, 0);
        builder.define(REAR_LEFT_WHEEL_TIGHTENING, 0);
        builder.define(REAR_RIGHT_WHEEL_TIGHTENING, 0);
        builder.define(SUSPENSION_COMPRESSION_0, 0.0F);
        builder.define(SUSPENSION_COMPRESSION_1, 0.0F);
        builder.define(SUSPENSION_COMPRESSION_2, 0.0F);
        builder.define(SUSPENSION_COMPRESSION_3, 0.0F);
        builder.define(JACK_ANCHOR_PRESENT, false);
        builder.define(JACK_ANCHOR_X_BITS, 0L);
        builder.define(JACK_ANCHOR_Y_BITS, 0L);
        builder.define(JACK_ANCHOR_Z_BITS, 0L);
        builder.define(SEAT_ASSIGNMENTS, encodeSeatAssignments(VehicleSeatAssignments.empty()));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putString(MAINTENANCE_STATE_KEY, maintenanceState.name());
        if (assemblyTablePos != null) {
            tag.putLong(ASSEMBLY_TABLE_POS_KEY, assemblyTablePos.asLong());
        }
        if (assemblyOwnerUuid != null) {
            tag.putUUID(ASSEMBLY_OWNER_KEY, assemblyOwnerUuid);
        }
        if (carrierUuid != null) {
            tag.putUUID(CARRIER_KEY, carrierUuid);
        }
        jackAnchor.ifPresent(anchor -> {
            tag.putDouble(JACK_ANCHOR_X_KEY, anchor.x);
            tag.putDouble(JACK_ANCHOR_Y_KEY, anchor.y);
            tag.putDouble(JACK_ANCHOR_Z_KEY, anchor.z);
        });
        tag.putBoolean(JACK_ITEM_STORED_KEY, jackItemStored);
        if (vehicleOwnerUuid != null) {
            tag.putUUID(VEHICLE_OWNER_UUID_KEY, vehicleOwnerUuid);
            tag.putString(VEHICLE_OWNER_NAME_KEY, vehicleOwnerName);
        }
        Pathway.either(CodecUtil.toEither(VehicleInstanceData.CODEC.encodeStart(NbtOps.INSTANCE, vehicleData)))
                .peekLeft(message -> PolarisVehicle.LOGGER.error(
                        "Failed to save vehicle data for entity {}: {}",
                        getId(),
                        message
                ))
                .peek(encoded -> tag.put(SAVE_KEY, encoded));
        Pathway.either(CodecUtil.toEither(VehicleSeatAssignments.CODEC.encodeStart(
                        NbtOps.INSTANCE,
                        seatAssignments
                )))
                .peekLeft(message -> PolarisVehicle.LOGGER.error(
                        "Failed to save seat assignments for vehicle entity {}: {}",
                        getId(),
                        message
                ))
                .peek(encoded -> tag.put(SEAT_ASSIGNMENTS_KEY, encoded));
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        VehicleInstanceData decoded = Pathway.nullable(tag.get(SAVE_KEY))
                .via(encoded -> Pathway.either(CodecUtil.toEither(
                                VehicleInstanceData.CODEC.parse(NbtOps.INSTANCE, encoded)
                        ))
                        .peekLeft(message -> PolarisVehicle.LOGGER.error(
                                "Failed to load vehicle data for entity {}: {}",
                                getId(),
                                message
                        ))
                        .toMaybePath())
                .getOrElse(vehicleData);
        applyVehicleData(decoded, false);
        seatAssignments = Pathway.nullable(tag.get(SEAT_ASSIGNMENTS_KEY))
                .via(encoded -> Pathway.either(CodecUtil.toEither(
                                VehicleSeatAssignments.CODEC.parse(NbtOps.INSTANCE, encoded)
                        ))
                        .peekLeft(message -> PolarisVehicle.LOGGER.error(
                                "Failed to load seat assignments for vehicle entity {}: {}",
                                getId(),
                                message
                        ))
                        .toMaybePath())
                .getOrElse(VehicleSeatAssignments.empty());
        entityData.set(SEAT_ASSIGNMENTS, encodeSeatAssignments(seatAssignments));
        assemblyTablePos = tag.contains(ASSEMBLY_TABLE_POS_KEY)
                ? BlockPos.of(tag.getLong(ASSEMBLY_TABLE_POS_KEY))
                : null;
        assemblyOwnerUuid = tag.hasUUID(ASSEMBLY_OWNER_KEY)
                ? tag.getUUID(ASSEMBLY_OWNER_KEY)
                : null;
        carrierUuid = tag.hasUUID(CARRIER_KEY) ? tag.getUUID(CARRIER_KEY) : null;
        vehicleOwnerUuid = tag.hasUUID(VEHICLE_OWNER_UUID_KEY)
                ? tag.getUUID(VEHICLE_OWNER_UUID_KEY)
                : null;
        vehicleOwnerName = vehicleOwnerUuid == null
                ? ""
                : tag.getString(VEHICLE_OWNER_NAME_KEY);
        jackItemStored = tag.getBoolean(JACK_ITEM_STORED_KEY);
        entityData.set(CARRIER_ENTITY_ID, -1);
        boolean hasSavedJackAnchor = tag.contains(JACK_ANCHOR_X_KEY)
                && tag.contains(JACK_ANCHOR_Y_KEY)
                && tag.contains(JACK_ANCHOR_Z_KEY);
        if (hasSavedJackAnchor) {
            Vec3 savedAnchor = new Vec3(
                    tag.getDouble(JACK_ANCHOR_X_KEY),
                    tag.getDouble(JACK_ANCHOR_Y_KEY),
                    tag.getDouble(JACK_ANCHOR_Z_KEY)
            );
            setJackAnchor(savedAnchor);
        } else {
            clearJackAnchor();
        }
        assemblyValidationGraceTicks = 100;
        if (tag.contains(MAINTENANCE_STATE_KEY)) {
            try {
                setMaintenanceState(VehicleMaintenanceState.valueOf(
                        tag.getString(MAINTENANCE_STATE_KEY)), false);
            } catch (IllegalArgumentException exception) {
                PolarisVehicle.LOGGER.warn(
                        "Invalid maintenance state on vehicle entity {}, resetting to NORMAL",
                        getId()
                );
                setMaintenanceState(VehicleMaintenanceState.NORMAL, false);
            }
        }
        if (maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE
                && assemblyTablePos == null) {
            setMaintenanceState(VehicleMaintenanceState.NORMAL, false);
        }
        if (maintenanceState == VehicleMaintenanceState.CARRIED && carrierUuid == null) {
            setMaintenanceState(VehicleMaintenanceState.NORMAL, false);
        }
        if (maintenanceState == VehicleMaintenanceState.DISASSEMBLING
                || maintenanceState == VehicleMaintenanceState.REMOVED) {
            setMaintenanceState(VehicleMaintenanceState.NORMAL, false);
        }
        if (maintenanceState != VehicleMaintenanceState.JACKED
                && maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE
                && maintenanceState != VehicleMaintenanceState.CARRIED) {
            clearJackAnchor();
        }
        synchronizePoseRotation();
    }

    public final void initializePlacementPose(Vec3 position, float yawDegrees) {
        float wrappedYaw = Mth.wrapDegrees(yawDegrees);
        moveTo(position.x, position.y, position.z, wrappedYaw, 0.0F);
        setYBodyRot(wrappedYaw);
        synchronizePoseRotation();
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // Entity.recreateFromPacket applies the packet yaw after moveTo(), so
        // yRotO otherwise remains North for the first rendered frame. Synced
        // entity data also arrives after the add-entity packet; seed the
        // client quaternion from the packet yaw instead of rendering one frame
        // with the identity rotation.
        setOldPosAndRot();
        Quaternionf initialRotation = new Quaternionf()
                .rotateY((float) -Math.toRadians(getYRot()));
        clientBodyRotation.set(initialRotation);
        clientBodyRotationOld.set(initialRotation);
        interpolationTarget = position();
        interpolationTargetYaw = getYRot();
        interpolationTargetPitch = getXRot();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientBodyRotationOld.set(clientBodyRotation);
            if (maintenanceState() == VehicleMaintenanceState.CARRIED
                    && snapClientToCarrier()) {
                // The carried vehicle is a visual attachment. Render-time correction below
                // removes the remaining sub-tick ordering difference from its carrier.
            } else if (maintenanceState().locksPose()) {
                lockClientMaintenancePose();
            } else {
                tickClientInterpolation();
            }
            tickClientWheelRotation();
            tickClientSteeringAngle();
            tickClientSuspensionCompression();
            return;
        }

        if (maintenanceState == VehicleMaintenanceState.DISASSEMBLING
                || dismantlingPlayerUuid != null) {
            tickDismantleProgress();
        }
        if (maintenanceState == VehicleMaintenanceState.CARRIED) {
            tickCarriedState();
        }
        tickPlacementProtection();
        tickFuelingSession();
        tickJackLowering();
        if (maintenanceState.blocksDriving()) {
            clearDriverInput();
            setDeltaMovement(Vec3.ZERO);
            if (vehicleData.engineRunning() && !maintenanceState.allowsEngineOperation()) {
                updateVehicleData(data -> data.withEngineRunning(definition(), false), false);
            }
            if (maintenanceState == VehicleMaintenanceState.JACKED) {
                tickBoostTimers();
                tickPartAndFuelConsumption();
            }
            if (maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE) {
                if (assemblyValidationGraceTicks > 90) {
                    resendMaintenanceTransform();
                }
                validateAssemblyTableBinding();
            }
            return;
        }

        if (!(getControllingPassenger() instanceof ServerPlayer)
                || level().getGameTime() - lastDriverInputGameTime > 20L) {
            clearDriverInput();
        }

        tickBoostTimers();
        VehiclePhysicsProfile physicsProfile = VehiclePhysicsProfile.require(vehicleTypeId());
        var typeParameters = VehicleTypeParameters.resolve(vehicleTypeId());
        boolean sleepingPhysics = VehiclePhysicsManager.isSleeping(this);
        if (!sleepingPhysics || cachedParkedTerrain == null || tickCount % 20 == 0) {
            cachedParkedTerrain = VehicleWheelTerrainResolver.resolve(this);
        }
        VehicleWheelTerrain wheelTerrain = cachedParkedTerrain;
        boolean attended = getControllingPassenger() instanceof ServerPlayer;
        VehicleGameplayConfig gameplayConfig = VehicleGameplayConfig.get();
        int throttle = Integer.compare((inputForward ? 1 : 0) - (inputBackward ? 1 : 0), 0);
        slipstreamTrail.record(rearBumper());
        measuredSlipstreamBonus = sleepingPhysics ? 0 : slipstreamMaxSpeedBonus();
        appliedSlipstreamBonus = slipstreamHold.update(measuredSlipstreamBonus);
        VehiclePhysicsInput physicsInput = physicsInputCache.resolve(
                definition(), vehicleData, typeParameters, physicsProfile, wheelTerrain,
                appliedSlipstreamBonus,
                throttle,
                Integer.compare((inputRight ? 1 : 0) - (inputLeft ? 1 : 0), 0),
                inputBrake || (inputForward && inputBackward),
                attended,
                inputSteeringResponse
        );
        appliedMaxSpeed = physicsInput.performance().maxSpeedMetersPerSecond();
        if (!VehiclePhysicsManager.tickVehicle(this, physicsInput)) {
            resetSuspensionCompression();
            tickGroundMovement(physicsInput.performance());
            synchronizeFallbackBodyRotation();
        } else {
            fallbackThrottleResponse.reset();
        }
        entityData.set(PRECISE_YAW, getYRot());
        tickPartAndFuelConsumption();
    }

    @Override
    public void lerpTo(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            int positionRotationIncrements
    ) {
        if (!level().isClientSide()) {
            super.lerpTo(x, y, z, yaw, pitch, positionRotationIncrements);
            return;
        }
        if (maintenanceState().locksPose()) {
            return;
        }

        Vec3 target = new Vec3(x, y, z);
        float receivedYaw = clientHasPreciseYaw ? entityData.get(PRECISE_YAW) : yaw;
        float yawReference = clientSnapshots.isEmpty()
                ? getYRot()
                : clientSnapshots.getLast().yaw();
        float continuousYaw = MathUtil.unwrapDegrees(yawReference, receivedYaw);
        if (position().distanceToSqr(target) > CLIENT_TELEPORT_DISTANCE_SQUARED) {
            interpolationTarget = target;
            interpolationTargetYaw = continuousYaw;
            interpolationTargetPitch = pitch;
            hasClientInterpolationTarget = true;
            clientSnapshots.clear();
            clientSnapshotPlaybackStarted = false;
            resetLocalDriverExtrapolation();
            setPos(target);
            // Entity#setRot applies % 360 to both axes. Keep the unwrapped yaw
            // timeline intact so yRotO -> yRot never interpolates the long way
            // around when a client correction crosses the 360-degree boundary.
            setYRot(continuousYaw);
            setXRot(pitch);
            setYBodyRot(continuousYaw);
            setOldPosAndRot();
            setClientBodyRotation(entityData.get(BODY_ROTATION));
            clientBodyRotationOld.set(clientBodyRotation);
            clientTeleportAwaitingBodyRotation = true;
            return;
        }

        interpolationTarget = target;
        interpolationTargetYaw = continuousYaw;
        interpolationTargetPitch = pitch;
        hasClientInterpolationTarget = true;
        clientSnapshots.addLast(clientSnapshot(target, continuousYaw, pitch));
        clientSnapshotAwaitingPreciseYaw = true;
        clientSnapshotAwaitingBodyRotation = true;
        clientTeleportAwaitingBodyRotation = false;
        if (clientSnapshots.size() > CLIENT_SNAPSHOT_MAX_DEPTH) {
            while (clientSnapshots.size() > CLIENT_SNAPSHOT_OVERFLOW_RETAINED) {
                clientSnapshots.removeFirst();
            }
        }
    }

    @Override
    public double lerpTargetX() {
        return hasClientInterpolationTarget ? interpolationTarget.x : super.lerpTargetX();
    }

    @Override
    public double lerpTargetY() {
        return hasClientInterpolationTarget ? interpolationTarget.y : super.lerpTargetY();
    }

    @Override
    public double lerpTargetZ() {
        return hasClientInterpolationTarget ? interpolationTarget.z : super.lerpTargetZ();
    }

    @Override
    public float lerpTargetXRot() {
        return hasClientInterpolationTarget ? interpolationTargetPitch : super.lerpTargetXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return hasClientInterpolationTarget ? interpolationTargetYaw : super.lerpTargetYRot();
    }

    private boolean snapClientToCarrier() {
        Entity carrier = clientCarrier();
        if (carrier == null) {
            return false;
        }
        Vec3 target = carriedPosition(carrier, carrier.position(), carrier.tickCount);
        float yaw = MathUtil.unwrapDegrees(getYRot(), carrier.getYRot());
        clientSnapshots.clear();
        clientSnapshotPlaybackStarted = false;
        resetLocalDriverExtrapolation();
        clientSnapshotAwaitingPreciseYaw = false;
        clientSnapshotAwaitingBodyRotation = false;
        clientTeleportAwaitingBodyRotation = false;
        interpolationTarget = target;
        interpolationTargetYaw = yaw;
        interpolationTargetPitch = 0.0F;
        hasClientInterpolationTarget = true;
        setPos(target);
        setYRot(yaw);
        setXRot(0.0F);
        setYBodyRot(yaw);
        setClientBodyRotation(new Quaternionf().rotateY((float) -Math.toRadians(yaw)));
        return true;
    }

    private Entity clientCarrier() {
        if (!level().isClientSide()) {
            return null;
        }
        int carrierId = entityData.get(CARRIER_ENTITY_ID);
        return carrierId < 0 ? null : level().getEntity(carrierId);
    }

    private void tickClientInterpolation() {
        // Movement and entity-data packets for one server state are processed
        // before the entity tick. Do not let an unmatched movement packet make
        // a later yaw-only update overwrite the wrong snapshot.
        clientSnapshotAwaitingPreciseYaw = false;
        clientSnapshotAwaitingBodyRotation = false;
        // Keep one state of driver jitter tolerance: consuming every normal
        // two-packet burst immediately doubles one tick's visible displacement.
        int depthBeforePlayback = clientSnapshots.size();
        if (!VehicleSnapshotPlayback.prepare(
                clientSnapshots, clientSnapshotPlaybackStarted, isLocalDriverForPlayback())) return;
        clientSnapshotPlaybackStarted = true;
        if (clientSnapshots.isEmpty()) {
            // A brief packet gap must not restart the startup
            // buffer. Doing that turns one missed state into another 150 ms
            // freeze during ordinary driving. Keep the playback timeline
            // active so the next authoritative state is consumed immediately.
            extrapolateLocalDriverGap();
            return;
        }

        ClientSnapshot snapshot = clientSnapshots.removeFirst();
        Vec3 previousPosition = position();
        float previousYaw = getYRot();
        Quaternionf previousRotation = new Quaternionf(clientBodyRotation);
        float currentYaw = getYRot();
        float safeYaw = MathUtil.unwrapDegrees(currentYaw, snapshot.yaw());
        float revolutionOffset = safeYaw - snapshot.yaw();
        if (Math.abs(revolutionOffset) >= 180.0F) {
            rebaseClientYawTimeline(revolutionOffset);
        }
        setPos(snapshot.position());
        // Do not use Entity#setRot here: its % 360 normalization would turn a
        // continuous 360.324 yaw into 0.324 and make vanilla render interpolation
        // traverse an almost-complete revolution between adjacent client ticks.
        setYRot(safeYaw);
        setXRot(snapshot.pitch());
        setYBodyRot(safeYaw);
        setClientBodyRotation(snapshot.rotation());
        localDriverPose.consumed(previousPosition, previousYaw, previousRotation, position(), safeYaw,
                clientBodyRotation, depthBeforePlayback - clientSnapshots.size());
    }

    /**
     * See {@link com.flechazo.polaris_vehicle.vehicle.physics.VehicleDriverPoseExtrapolator}.
     */
    private void extrapolateLocalDriverGap() {
        if (!localDriverPose.gap(isLocalDriverForPlayback())) return;
        setPos(position().add(localDriverPose.positionStep()));
        float yaw = getYRot() + localDriverPose.yawStep();
        setYRot(yaw);
        setYBodyRot(yaw);
        setClientBodyRotation(localDriverPose.rotate(clientBodyRotation));
    }

    private void resetLocalDriverExtrapolation() {
        localDriverPose.reset();
    }

    private boolean isLocalDriverForPlayback() {
        // isControlledByLocalInstance deliberately stays false to suppress
        // vanilla client-authored vehicle movement packets. It must not also
        // select the observer's three-state delay for the actual local driver.
        return getControllingPassenger() instanceof Player player && player.isLocalPlayer();
    }

    private void rebaseClientYawTimeline(float revolutionOffset) {
        interpolationTargetYaw += revolutionOffset;
        int pendingSnapshots = clientSnapshots.size();
        for (int index = 0; index < pendingSnapshots; index++) {
            ClientSnapshot pending = clientSnapshots.removeFirst();
            clientSnapshots.addLast(pending.withYaw(pending.yaw() + revolutionOffset));
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide()) {
            return;
        }

        if (ACTUAL_STEERING.equals(key)) {
            clientSteeringTimeline.accept(entityData.get(ACTUAL_STEERING), System.nanoTime() * 1E-9);
            return;
        }

        if (MAINTENANCE_STATE.equals(key)) {
            if (maintenanceState().locksPose()) {
                lockClientMaintenancePose();
            }
            return;
        }

        if (SEAT_ASSIGNMENTS.equals(key)) {
            seatAssignments = CodecUtil.toEither(VehicleSeatAssignments.CODEC.parse(
                            NbtOps.INSTANCE,
                            entityData.get(SEAT_ASSIGNMENTS)
                    ))
                    .peekLeft(message -> PolarisVehicle.LOGGER.warn(
                            "Ignoring invalid seat assignments for client vehicle {}: {}",
                            getId(),
                            message
                    ))
                    .getOrElse(seatAssignments);
            for (Entity passenger : getPassengers()) {
                positionRider(passenger);
            }
            return;
        }

        if (BODY_ROTATION.equals(key)) {
            Quaternionf rotation = sanitizedRotation(entityData.get(BODY_ROTATION));
            if (maintenanceState().locksPose()) {
                setClientBodyRotation(rotation);
                clientBodyRotationOld.set(clientBodyRotation);
                clientSnapshotAwaitingBodyRotation = false;
                return;
            }
            if (clientSnapshotAwaitingBodyRotation && !clientSnapshots.isEmpty()) {
                ClientSnapshot latest = clientSnapshots.removeLast();
                clientSnapshots.addLast(latest.withRotation(rotation));
            } else if (clientTeleportAwaitingBodyRotation) {
                setClientBodyRotation(rotation);
                clientBodyRotationOld.set(clientBodyRotation);
                clientTeleportAwaitingBodyRotation = false;
            }
            clientSnapshotAwaitingBodyRotation = false;
            return;
        }
        if (!PRECISE_YAW.equals(key)) {
            return;
        }

        clientHasPreciseYaw = true;
        float preciseYaw = entityData.get(PRECISE_YAW);
        if (maintenanceState().locksPose()) {
            float lockedYaw = MathUtil.unwrapDegrees(getYRot(), preciseYaw);
            setYRot(lockedYaw);
            setYBodyRot(lockedYaw);
            interpolationTargetYaw = lockedYaw;
            clientSnapshots.clear();
            clientSnapshotPlaybackStarted = false;
            resetLocalDriverExtrapolation();
            clientSnapshotAwaitingPreciseYaw = false;
            return;
        }
        if (clientSnapshotAwaitingPreciseYaw && !clientSnapshots.isEmpty()) {
            ClientSnapshot latest = clientSnapshots.removeLast();
            float yawReference = clientSnapshots.isEmpty()
                    ? getYRot()
                    : clientSnapshots.getLast().yaw();
            float continuousYaw = MathUtil.unwrapDegrees(yawReference, preciseYaw);
            interpolationTargetYaw = continuousYaw;
            clientSnapshots.addLast(latest.withYaw(continuousYaw));
        } else {
            float continuousYaw = MathUtil.unwrapDegrees(getYRot(), preciseYaw);
            interpolationTargetYaw = continuousYaw;
            if (hasClientInterpolationTarget
                    && Math.abs(Mth.wrapDegrees(continuousYaw - getYRot())) > 1.0E-4F) {
                clientSnapshots.addLast(clientSnapshot(
                        interpolationTarget,
                        continuousYaw,
                        interpolationTargetPitch
                ));
            }
        }
        clientSnapshotAwaitingPreciseYaw = false;
    }

    private void lockClientMaintenancePose() {
        clientSnapshots.clear();
        clientSnapshotPlaybackStarted = false;
        resetLocalDriverExtrapolation();
        clientSnapshotAwaitingPreciseYaw = false;
        clientSnapshotAwaitingBodyRotation = false;
        clientTeleportAwaitingBodyRotation = false;
        float lockedYaw = MathUtil.unwrapDegrees(
                getYRot(),
                entityData.get(PRECISE_YAW)
        );
        interpolationTarget = position();
        interpolationTargetYaw = lockedYaw;
        interpolationTargetPitch = getXRot();
        hasClientInterpolationTarget = true;
        setYRot(lockedYaw);
        setYBodyRot(lockedYaw);
        setClientBodyRotation(entityData.get(BODY_ROTATION));
        clientBodyRotationOld.set(clientBodyRotation);
    }

    private ClientSnapshot clientSnapshot(Vec3 position, float yaw, float pitch) {
        return new ClientSnapshot(position, yaw, pitch, sanitizedRotation(entityData.get(BODY_ROTATION)));
    }

    /**
     * Applies a discrete jack/table transfer without passing through the
     * driving jitter buffer. Normal physics updates continue to use lerpTo.
     */
    public final void applyMaintenanceTransform(Vec3 target, float yaw, float pitch) {
        if (!level().isClientSide()) {
            return;
        }
        float continuousYaw = MathUtil.unwrapDegrees(getYRot(), yaw);
        clientSnapshots.clear();
        clientSnapshotPlaybackStarted = false;
        resetLocalDriverExtrapolation();
        clientSnapshotAwaitingPreciseYaw = false;
        clientSnapshotAwaitingBodyRotation = false;
        clientTeleportAwaitingBodyRotation = false;
        interpolationTarget = target;
        interpolationTargetYaw = continuousYaw;
        interpolationTargetPitch = pitch;
        hasClientInterpolationTarget = true;
        setPos(target);
        setYRot(continuousYaw);
        setXRot(pitch);
        setYBodyRot(continuousYaw);
        setOldPosAndRot();
        // The maintenance transform packet is deliberately immediate, while the matching
        // SynchedEntityData BODY_ROTATION update may arrive a tick later. Rendering that old
        // quaternion made a vehicle snap into place first and rotate afterwards. Derive the
        // same yaw-only maintenance pose directly from this atomic position/yaw packet.
        setClientBodyRotation(new Quaternionf().rotateY(
                (float) -Math.toRadians(continuousYaw)
        ));
        clientBodyRotationOld.set(clientBodyRotation);
    }

    private void setClientBodyRotation(Quaternionf requestedRotation) {
        Quaternionf safeRotation = sanitizedRotation(requestedRotation);
        if (clientBodyRotation.dot(safeRotation) < 0.0F) {
            safeRotation.set(
                    -safeRotation.x(),
                    -safeRotation.y(),
                    -safeRotation.z(),
                    -safeRotation.w()
            );
        }
        clientBodyRotation.set(safeRotation);
    }

    private static Quaternionf sanitizedRotation(Quaternionf requestedRotation) {
        Quaternionf copy = new Quaternionf(requestedRotation);
        float lengthSquared = copy.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-8F) {
            return new Quaternionf();
        }
        return copy.normalize();
    }

    public final Quaternionf vehicleBodyRenderRotation(float partialTick) {
        if (!level().isClientSide()) {
            return sanitizedRotation(entityData.get(BODY_ROTATION));
        }
        Entity carrier = clientCarrier();
        if (maintenanceState() == VehicleMaintenanceState.CARRIED && carrier != null) {
            float carrierYaw = Mth.rotLerp(
                    Math.clamp(partialTick, 0.0F, 1.0F),
                    carrier.yRotO,
                    carrier.getYRot()
            );
            return new Quaternionf().rotateY((float) -Math.toRadians(carrierYaw));
        }
        return new Quaternionf(clientBodyRotationOld)
                .slerp(clientBodyRotation, Math.clamp(partialTick, 0.0F, 1.0F));
    }

    /**
     * Uses the carrier's exact partial-tick coordinates, eliminating network follow delay.
     */
    public final Vec3 carriedRenderOffset(float partialTick) {
        if (!level().isClientSide()
                || maintenanceState() != VehicleMaintenanceState.CARRIED) {
            return Vec3.ZERO;
        }
        Entity carrier = clientCarrier();
        if (carrier == null) {
            return Vec3.ZERO;
        }
        float frame = Math.clamp(partialTick, 0.0F, 1.0F);
        Vec3 desired = carriedPosition(
                carrier,
                carrier.getPosition(frame),
                carrier.tickCount + frame
        );
        return desired.subtract(getPosition(frame));
    }

    /**
     * Current played body orientation for collision queries and culling.
     */
    public final Quaternionf vehicleBodyRotation() {
        return level().isClientSide()
                ? new Quaternionf(clientBodyRotation)
                : sanitizedRotation(entityData.get(BODY_ROTATION));
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> model.worldBounds(position(), vehicleBodyRotation()))
                .orElse(getBoundingBox());
    }

    /**
     * Precise full-cube query for later entity, ray and block contact paths.
     */
    public final boolean intersectsVehicleStructure(AABB worldBounds, boolean solidOnly) {
        return intersectsVehicleStructure(
                worldBounds,
                VehicleCollisionRole.ALL_MASK & ~VehicleCollisionRole.SENSOR.mask(),
                solidOnly
        );
    }

    public final boolean intersectsVehicleStructure(
            AABB worldBounds,
            int roleMask,
            boolean solidOnly
    ) {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> model.intersectsWorldAabb(
                        position(),
                        vehicleBodyRotation(),
                        worldBounds,
                        roleMask,
                        solidOnly
                ))
                .orElseGet(() -> getBoundingBox().intersects(worldBounds));
    }

    public final Maybe<Vec3> resolveStructureMovement(Vec3 requestedMovement) {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> VehicleBlockCollisionResolver.resolve(
                        this,
                        model,
                        requestedMovement
                ));
    }

    private float resolveStructureYaw(float requestedYaw) {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> VehicleBlockCollisionResolver.resolveYaw(
                        this,
                        model,
                        requestedYaw
                ))
                .orElse(requestedYaw);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide() && maintenanceState == VehicleMaintenanceState.CARRIED) {
            VehicleCarryTracker.track(this, carrierUuid);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        if (!level().isClientSide()) {
            // NeoForge also invokes this when an entity stops being visible
            // during a move into an unloaded chunk. That is not destruction;
            // the carry ticket/index must survive until the destination loads.
            if (isRemoved()) VehicleCarryTracker.untrack(this);
            clearFuelingSession();
        }
        VehiclePhysicsManager.removeVehicle(this);
        super.onRemovedFromLevel();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    /**
     * Bullet resolves every contact with the physical chassis. Vanilla entity
     * pushing (a player standing against the car) only adds to deltaMovement,
     * which a sleeping physics body never overwrites: it piled up into a large
     * fake velocity that parked cars synced to clients, showing slipstream
     * streaks and jerking the car when its driver got in. The player is still
     * pushed away from the vehicle by Entity#push(Entity).
     */
    @Override
    public void push(double x, double y, double z) {
    }

    /**
     * Vehicle physics are authoritative on the logical server. Returning
     * false on the client prevents LocalPlayer from sending vanilla
     * ServerboundMoveVehiclePacket positions back over the server result and
     * allows the client packet listener to apply the server interpolation
     * targets instead.
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return !level().isClientSide();
    }

    @Override
    public float maxUpStep() {
        if (vehicleTypeId().equals(VehicleDefinitions.FORMULA_CAR_ID)) {
            return 0.0F;
        }
        return (float) VehicleTypeParameters.resolve(vehicleTypeId()).physics().maximumStepHeight();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult parentResult = super.interact(player, hand);
        if (parentResult != InteractionResult.PASS) {
            return parentResult;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (entityData.get(JACK_LOWERING)) return InteractionResult.sidedSuccess(level().isClientSide());
        switch (held.getItem()) {
            case VehicleKeyItem vehicleKeyItem -> {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                return bindVehicleOwner(player, held)
                        ? InteractionResult.CONSUME
                        : InteractionResult.FAIL;
            }
            case VehicleFuelTankItem vehicleFuelTankItem -> {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                boolean aimedAtPort = isFuelPortAimedAt(player);
                if (!aimedAtPort) {
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.fueling.aim_at_port"), true);
                    return InteractionResult.CONSUME;
                }
                return handleFuelingInteraction(player, hand)
                        ? InteractionResult.CONSUME
                        : InteractionResult.PASS;
            }
            case VehiclePaintItem paintItem -> {
                if (level().isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                return applyPaint(player, held, paintItem)
                        ? InteractionResult.CONSUME
                        : InteractionResult.PASS;
            }
            default -> {
            }
        }
        if (!level().isClientSide() && handleCarryInteraction(player)) {
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide() && handleJackInteraction(player)) {
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide() && handleMaintenanceInteraction(player)) {
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide() && handleAssemblyBodyInteraction(player)) {
            return InteractionResult.CONSUME;
        }
        if (!maintenanceState().allowsBoarding()) {
            if (!level().isClientSide() && maintenanceState() == VehicleMaintenanceState.JACKED) {
                player.displayClientMessage(Component.translatable("message.polaris_vehicle.seat.jacked"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            attemptSeatBoarding(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactAt(
            Player player,
            Vec3 localHitPosition,
            InteractionHand hand
    ) {
        if (hand == InteractionHand.MAIN_HAND
                && player.getItemInHand(hand).getItem() instanceof VehicleFuelTankItem) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            Vec3 hitPosition = position().add(localHitPosition);
            boolean hitPort = isFuelPortHit(hitPosition);
            boolean aimedAtPort = isFuelPortAimedAt(player);
            if (!hitPort && !aimedAtPort) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.fueling.aim_at_port"), true);
                return InteractionResult.CONSUME;
            }
            return handleFuelingInteraction(player, hand)
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }
        if (!isSeatBoardingInteraction(player, hand)) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            attemptSeatBoarding(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    private boolean isSeatBoardingInteraction(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND
                || player.isSecondaryUseActive()
                || !maintenanceState().allowsBoarding()
                || ModItemTags.holdsVehicleTool(player)) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        return !(held.getItem() instanceof VehicleFuelTankItem)
                && !(held.getItem() instanceof VehiclePaintItem)
                && !(held.getItem() instanceof VehicleJackItem);
    }

    private void attemptSeatBoarding(Player player) {
        if (level().isClientSide()) {
            return;
        }
        if (ModItemTags.holdsVehicleTool(player)) {
            player.displayClientMessage(Component.translatable("message.polaris_vehicle.seat.tool_in_hand"), true);
            return;
        }
        double interactionRange = player.entityInteractionRange();
        Vec3 eyeWorld = player.getEyePosition();
        Vec3 viewWorld = player.getViewVector(1.0F).normalize();
        float inverseVehicleYaw = (float) Math.toRadians(getYRot());
        Vec3 eyeLocal = eyeWorld
                .subtract(position())
                .yRot(inverseVehicleYaw);
        Vec3 viewLocal = viewWorld.yRot(inverseVehicleYaw).normalize();
        List<VehicleSeatSelector.Candidate> candidates = new ArrayList<>(
                seatLayout().seats().size()
        );
        for (int seatIndex = 0; seatIndex < seatLayout().seats().size(); seatIndex++) {
            Vec3 localCenter = seatLayout().seats().get(seatIndex);
            Vec3 worldVisibilityPoint = seatWorldVisibilityPoint(player, seatIndex);
            candidates.add(new VehicleSeatSelector.Candidate(
                    seatIndex,
                    localCenter,
                    isSeatOccupied(seatIndex, player),
                    isSeatObstructed(player, eyeWorld, worldVisibilityPoint)
            ));
        }
        VehicleSeatSelector.select(
                eyeLocal,
                viewLocal,
                interactionRange,
                candidates
        ).fold(
                () -> {
                    player.displayClientMessage(noSeatTargetMessage(candidates), true);
                    return false;
                },
                selection -> boardSelectedSeat(player, interactionRange, selection)
        );
    }

    public final void tryStartBoardingFromTargetRequest(ServerPlayer player) {
        if (player == null
                || player.level() != level()
                || !isAlive()
                || player.getVehicle() != null
                || !isSeatBoardingInteraction(player, InteractionHand.MAIN_HAND)
                || distanceToSqr(player) > Math.pow(player.entityInteractionRange() + 1.0, 2.0)) {
            return;
        }
        attemptSeatBoarding(player);
    }

    private static Component noSeatTargetMessage(
            List<VehicleSeatSelector.Candidate> candidates
    ) {
        if (candidates.stream().allMatch(VehicleSeatSelector.Candidate::occupied)) {
            return Component.translatable("message.polaris_vehicle.seat.all_occupied");
        }
        if (candidates.stream()
                .filter(candidate -> !candidate.occupied())
                .allMatch(VehicleSeatSelector.Candidate::obstructed)) {
            return Component.translatable("message.polaris_vehicle.seat.all_obstructed");
        }
        return Component.translatable("message.polaris_vehicle.seat.no_target");
    }

    private boolean boardSelectedSeat(
            Player player,
            double interactionRange,
            VehicleSeatSelector.Selection selection
    ) {
        if (ModItemTags.holdsVehicleTool(player)) return false;
        VehicleSeatSelector.Candidate candidate = selection.candidate();
        if (selection.targetDistance() > interactionRange) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.seat.too_far"), true);
            return false;
        }
        if (candidate.occupied()) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.seat.occupied"), true);
            return false;
        }
        if (candidate.obstructed()) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.seat.obstructed"), true);
            return false;
        }

        VehicleSeatAssignments previous = seatAssignments;
        setSeatAssignments(seatAssignments.assign(player.getUUID(), candidate.seatIndex()));
        if (player.startRiding(this)) {
            return true;
        }
        setSeatAssignments(previous);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.seat.unavailable"), true);
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player player)
                || source.getDirectEntity() != player) {
            return super.hurt(source, amount);
        }
        if (!level().isClientSide() && player.getMainHandItem().getItem() instanceof VehicleHammerItem) {
            logDismantleState("hurt_received", player, "damage=" + amount);
        }
        if (player.getAbilities().instabuild) {
            if (!(player.getMainHandItem().getItem() instanceof VehicleHammerItem)) {
                if (!level().isClientSide()) {
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.creative_remove_needs_hammer"), true);
                }
                return false;
            }
            if (!level().isClientSide()) {
                logDismantleState("creative_discard", player, "creative attack bypasses dismantle progress");
                discard();
            }
            return true;
        }
        if (!(player.getMainHandItem().getItem() instanceof VehicleHammerItem)) {
            return super.hurt(source, amount);
        }
        if (level().isClientSide()) {
            return true;
        }
        return player instanceof ServerPlayer serverPlayer
                && handleDismantleHit(serverPlayer);
    }

    private boolean handleDismantleHit(ServerPlayer player) {
        if (!canContinueDismantling(player)) {
            List<String> reasons = new ArrayList<>();
            if (maintenanceState != VehicleMaintenanceState.NORMAL
                    && maintenanceState != VehicleMaintenanceState.DISASSEMBLING
                    && maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE)
                reasons.add("invalid_maintenance_state");
            if (!getPassengers().isEmpty()) reasons.add("passengers_present");
            if (horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) reasons.add("vehicle_not_stationary");
            if (vehicleData.engineRunning()) reasons.add("engine_running");
            if (player.distanceToSqr(this) > DISMANTLE_DISTANCE_SQUARED) reasons.add("player_too_far");
            logDismantleState("rejected", player, String.join(",", reasons));
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.dismantle.invalid"), true);
            return true;
        }
        if (dismantlingPlayerUuid != null
                && !dismantlingPlayerUuid.equals(player.getUUID())) {
            logDismantleState("rejected", player, "another_player_dismantling=" + dismantlingPlayerUuid);
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.dismantle.busy"), true);
            return true;
        }
        if (dismantlingPlayerUuid == null) {
            dismantlingPlayerUuid = player.getUUID();
            dismantleHits = 0;
            if (maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE) {
                setMaintenanceState(VehicleMaintenanceState.DISASSEMBLING, true);
            }
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.dismantle.warning"), false);
        }
        dismantleHits++;
        dismantleDeadlineGameTime = level().getGameTime()
                + DISMANTLE_TIMEOUT_TICKS;
        logDismantleState("hit_accepted", player, "deadline=" + dismantleDeadlineGameTime);
        player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        ServerLevel serverLevel = (ServerLevel) level();
        serverLevel.playSound(
                null,
                blockPosition(),
                SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS,
                0.35F,
                1.35F
        );
        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                getX(), getY() + 0.75, getZ(),
                6,
                0.35, 0.25, 0.35,
                0.05
        );
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.dismantle.progress",
                dismantleHits,
                DISMANTLE_REQUIRED_HITS
        ), true);
        if (dismantleHits >= DISMANTLE_REQUIRED_HITS) {
            completeDismantling(player, serverLevel);
        }
        return true;
    }

    private boolean canContinueDismantling(ServerPlayer player) {
        boolean validState = maintenanceState == VehicleMaintenanceState.NORMAL
                || maintenanceState == VehicleMaintenanceState.DISASSEMBLING
                || maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE;
        return validState
                && getPassengers().isEmpty()
                && horizontalSpeedMetersPerSecond() <= MAINTENANCE_SPEED_EPSILON
                && !vehicleData.engineRunning()
                && player.distanceToSqr(this) <= DISMANTLE_DISTANCE_SQUARED;
    }

    private void tickDismantleProgress() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = dismantlingPlayerUuid == null
                ? null
                : serverLevel.getPlayerByUUID(dismantlingPlayerUuid);
        boolean invalid = player == null
                || !player.isAlive()
                || !(player.getMainHandItem().getItem() instanceof VehicleHammerItem)
                || player.distanceToSqr(this) > DISMANTLE_DISTANCE_SQUARED
                || serverLevel.getGameTime() > dismantleDeadlineGameTime
                || !getPassengers().isEmpty();
        if (invalid) {
            if (dismantlingPlayerUuid != null) {
                List<String> reasons = new ArrayList<>();
                if (player == null) reasons.add("player_missing=" + dismantlingPlayerUuid);
                else {
                    if (!player.isAlive()) reasons.add("player_not_alive");
                    if (!(player.getMainHandItem().getItem() instanceof VehicleHammerItem))
                        reasons.add("hammer_not_held");
                    if (player.distanceToSqr(this) > DISMANTLE_DISTANCE_SQUARED) reasons.add("player_too_far");
                }
                if (serverLevel.getGameTime() > dismantleDeadlineGameTime)
                    reasons.add("timeout_deadline=" + dismantleDeadlineGameTime);
                if (!getPassengers().isEmpty()) reasons.add("passengers_present");
                logDismantleState("progress_reset", player, String.join(",", reasons));
            }
            resetDismantling();
        }
    }

    private void completeDismantling(ServerPlayer player, ServerLevel serverLevel) {
        logDismantleState("completion_begin", player, "resolving_body_material_returns");
        var returns = VehicleDismantleReturns.forVehicle(serverLevel, vehicleTypeId());
        if (returns.isEmpty()) {
            logDismantleState("completion_failed", player, "return_materials_empty");
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.dismantle.drop_failed"), true);
            resetDismantling();
            return;
        }
        List<ItemStack> remaining = new ArrayList<>(returns.size());
        for (ItemStack stack : returns) {
            ItemStack pending = stack.copy();
            player.getInventory().add(pending);
            logDismantleState("inventory_return", player,
                    "item=" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " requested=" + stack.getCount()
                            + " inserted=" + (stack.getCount() - pending.getCount()) + " remaining=" + pending.getCount());
            if (!pending.isEmpty()) {
                remaining.add(pending.copy());
            }
        }
        for (ItemStack stack : remaining) {
            ItemEntity itemEntity = player.drop(stack.copy(), false);
            logDismantleState("drop_return", player,
                    "item=" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " count=" + stack.getCount()
                            + " dropped_entity=" + (itemEntity == null ? "null"
                            : itemEntity.getId() + ":alive=" + itemEntity.isAlive()
                              + ":removed=" + itemEntity.isRemoved() + ":pos=" + itemEntity.position()));
            if (itemEntity == null || !itemEntity.isAlive()) {
                logDismantleState("completion_failed", player, "drop_return_failed; vehicle retained");
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.dismantle.drop_failed"), true);
                resetDismantling();
                return;
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.dismantle.complete"), false);
        clearAssemblyBindingAfterDismantle();
        dismantlingPlayerUuid = null;
        dismantleHits = 0;
        dismantleDeadlineGameTime = 0L;
        setMaintenanceState(VehicleMaintenanceState.REMOVED, true);
        discard();
        VehicleDismantleNotices.vehicleDismantled(this, player, vehicleOwnerUuid);
        logDismantleState("completion_success", player, "body_materials_returned_and_vehicle_discarded");
    }

    private void logDismantleState(String stage, Player player, String detail) {
        if (level().isClientSide()) return;
        PolarisVehicle.LOGGER.debug(
                "[vehicle-dismantle] stage={} entity={} uuid={} type={} dimension={} tick={} player={}"
                        + " state={} vehiclePos={} playerPos={} hits={}/{} speedMps={} speedLimitMps={} deltaMovement={}"
                        + " engineRunning={} passengers={} distanceSq={} distanceLimitSq={} tablePos={} tableOwner={}"
                        + " tableBlock={} removed={} detail={}",
                stage, getId(), getUUID(), vehicleTypeId(), level().dimension().location(), level().getGameTime(),
                player == null ? "none" : player.getGameProfile().getName(), maintenanceState, position(),
                player == null ? "none" : player.position(), dismantleHits, DISMANTLE_REQUIRED_HITS,
                horizontalSpeedMetersPerSecond(), MAINTENANCE_SPEED_EPSILON, getDeltaMovement(),
                vehicleData.engineRunning(), getPassengers().size(),
                player == null ? "none" : player.distanceToSqr(this), DISMANTLE_DISTANCE_SQUARED,
                assemblyTablePos, assemblyOwnerUuid,
                assemblyTablePos == null ? "none" : BuiltInRegistries.BLOCK.getKey(level().getBlockState(assemblyTablePos).getBlock()),
                isRemoved(), detail);
    }

    private void clearAssemblyBindingAfterDismantle() {
        if (assemblyTablePos == null) {
            return;
        }
        if (level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table) {
            table.forgetDismantledVehicle(this);
        }
        assemblyTablePos = null;
        assemblyOwnerUuid = null;
        assemblyValidationGraceTicks = 0;
    }

    private void resetDismantling() {
        dismantlingPlayerUuid = null;
        dismantleHits = 0;
        dismantleDeadlineGameTime = 0L;
        if (maintenanceState == VehicleMaintenanceState.DISASSEMBLING) {
            setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
        }
    }

    private boolean handleMaintenanceInteraction(Player player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean holdingWheel = held.getItem() instanceof VehiclePartItem partItem
                && partItem.category()
                == VehiclePartCategory.WHEEL;
        if (!maintenanceState.allowsMaintenance()) {
            if (holdingWheel) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.wheel.requires_jack"), true);
                return true;
            }
            return false;
        }
        Maybe<VehiclePartSlot> selectedSlot = findWheelSlot(player, false);
        if (selectedSlot.isEmpty()) {
            if (holdingWheel) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.wheel.no_target"), true);
                return true;
            }
            return false;
        }
        VehiclePartSlot slot = selectedSlot.get();
        Maybe<InstalledPart> installed = vehicleData.part(slot);
        if (held.getItem() instanceof VehiclePartItem partItem) {
            if (partItem.category()
                    != VehiclePartCategory.WHEEL) {
                return false;
            }
            if (installed.isDefined()) {
                InstalledPart installedWheel = installed.get();
                player.displayClientMessage(installedWheel.isScrapped()
                        ? Component.translatable(
                        "message.polaris_vehicle.wheel.slot_occupied_scrapped")
                        : installedWheel.isFullyTightened()
                          ? Component.translatable(
                        "message.polaris_vehicle.wheel.slot_occupied_tight")
                          : Component.translatable(
                        "message.polaris_vehicle.wheel.slot_occupied_loose",
                        installedWheel.tightening(),
                        4), true);
                return true;
            }
            return vehicleData.withPart(definition(), slot, partItem.readPart(held)).fold(
                    error -> {
                        player.displayClientMessage(Component.translatable(
                                "message.polaris_vehicle.wheel.install_failed",
                                error
                        ), true);
                        return true;
                    },
                    updated -> {
                        applyVehicleData(updated, true);
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        player.swing(InteractionHand.MAIN_HAND, true);
                        return true;
                    }
            );
        }
        if (held.getItem() instanceof VehicleWrenchItem wrench) {
            return installed.fold(
                    () -> true,
                    installedPart -> {
                        int delta = player.isSecondaryUseActive() ? -1 : 1;
                        int target = wrench.electric()
                                ? (delta > 0 ? 4 : 0)
                                : installedPart.tightening() + delta;
                        int tightenedTarget = Math.clamp(target, 0, 4);
                        if (tightenedTarget != installedPart.tightening()) {
                            updateVehicleData(data -> data.withPart(
                                    definition(),
                                    slot,
                                    installedPart.withTightening(tightenedTarget)
                            ).getOrElse(data));
                            playToolSound(
                                    wrench.electric()
                                            ? ModSounds.WRENCH_PNEUMATIC.get()
                                            : ModSounds.WRENCH_MANUAL.get(),
                                    wrench.electric() ? 0.85F : 0.7F,
                                    wrench.electric() ? 1.0F : 0.95F
                            );
                            if (!player.getAbilities().instabuild) {
                                held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                            }
                            player.swing(InteractionHand.MAIN_HAND, true);
                        }
                        return true;
                    }
            );
        }
        if (held.isEmpty()) {
            return installed.filter(part -> part.tightening() == 0).fold(
                    () -> false,
                    installedPart -> {
                        ItemStack dropped = stackForPart(installedPart);
                        if (dropped.isEmpty()) {
                            PolarisVehicle.LOGGER.error(
                                    "Cannot remove unregistered vehicle part {} from entity {}",
                                    installedPart.partId(),
                                    getId()
                            );
                            return true;
                        }
                        VehicleInstanceData original = vehicleData;
                        updateVehicleData(data -> data.withoutPart(definition(), slot));
                        if (!player.addItem(dropped) && player.drop(dropped, false) == null) {
                            updateVehicleData(ignored -> original);
                            PolarisVehicle.LOGGER.error(
                                    "Could not safely return wheel {} removed from entity {}",
                                    installedPart.partId(),
                                    getId()
                            );
                        }
                        player.swing(InteractionHand.MAIN_HAND, true);
                        return true;
                    }
            );
        }
        return false;
    }

    private boolean handleJackInteraction(Player player) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        boolean holdingJack = held.getItem() instanceof VehicleJackItem;
        if (maintenanceState == VehicleMaintenanceState.NORMAL && holdingJack) {
            if (!authorizeLockedVehicleOperation(player)) {
                return true;
            }
            if (horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.jack.vehicle_busy"), true);
                return true;
            }
            setDeltaMovement(Vec3.ZERO);
            VehiclePhysicsManager.removeVehicle(this);
            Vec3 anchor = groundedJackBase(position());
            setJackAnchor(anchor);
            setMaintenanceState(VehicleMaintenanceState.JACKED, true);
            entityData.set(JACK_LIFT_START_TICK, level().getGameTime());
            setXRot(0.0F);
            synchronizePoseRotation();
            setMaintenancePosition(jackSupportedPosition(anchor));
            jackItemStored = !player.getAbilities().instabuild;
            if (jackItemStored) {
                held.shrink(1);
            }
            playToolSound(ModSounds.JACK.get(), 0.85F, 1.0F);
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.jack.raised"), true);
            return true;
        }
        if (VehicleMaintenanceControls.lowersJack(
                maintenanceState, held.isEmpty(), player.isSecondaryUseActive())) {
            return jackAnchor.fold(
                    () -> {
                        player.displayClientMessage(Component.translatable(
                                "message.polaris_vehicle.jack.anchor_missing"), true);
                        return true;
                    },
                    anchor -> lowerFromJack(player, anchor)
            );
        }
        return false;
    }

    private boolean lowerFromJack(Player player, Vec3 anchor) {
        return lowerFromJack(player, anchor, true);
    }

    private boolean lowerFromJack(Player player, Vec3 anchor, boolean animate) {
        Vec3 target = groundedVehiclePosition(anchor);
        Vec3 requested = target.subtract(position());
        if (requested.y >= -JACK_LOWER_EPSILON) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.jack.blocked"), true);
            return true;
        }
        AABB sweep = getBoundingBox().expandTowards(requested);
        List<VoxelShape> entityCollisions = level()
                .getEntities(
                        this,
                        sweep.inflate(1.0E-7),
                        EntitySelector.NO_SPECTATORS
                                .and(this::canCollideWith)
                                .and(entity -> entity != player)
                )
                .stream()
                .map(entity -> Shapes.create(entity.getBoundingBox()))
                .toList();
        Vec3 resolved = Entity.collideBoundingBox(
                this,
                requested,
                getBoundingBox(),
                level(),
                entityCollisions
        );
        double shortfall = resolved.y - requested.y;
        if (resolved.y >= -JACK_LOWER_EPSILON
                || shortfall > JACK_LOWER_MAX_SHORTFALL) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.jack.blocked"), true);
            return true;
        }
        Vec3 resolvedTarget = position().add(resolved);
        Maybe<Vec3> safePlayerPosition = safePlayerPositionForVehicle(player, resolvedTarget);
        if (safePlayerPosition.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.jack.blocked"), true);
            return true;
        }
        if (animate) {
            jackLoweringPlayer = player.getUUID();
            jackLoweringEndTick = level().getGameTime() + VehicleJackAnimationTiming.COMPLETION_TICKS;
            entityData.set(JACK_LIFT_START_TICK, level().getGameTime());
            entityData.set(JACK_LOWERING, true);
            playToolSound(ModSounds.JACK.get(), 0.85F, 0.9F);
            return true;
        }
        setMaintenancePosition(resolvedTarget);
        movePlayerToSafePosition(player, safePlayerPosition.get());
        clearJackAnchor();
        setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
        returnStoredJack(player);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.jack.lowered"), true);
        return true;
    }

    private void tickJackLowering() {
        if (!entityData.get(JACK_LOWERING) || level().getGameTime() < jackLoweringEndTick) return;
        Player player = jackLoweringPlayer == null ? null : level().getPlayerByUUID(jackLoweringPlayer);
        entityData.set(JACK_LOWERING, false);
        entityData.set(JACK_LIFT_START_TICK, Long.MIN_VALUE);
        jackLoweringPlayer = null;
        if (player != null && maintenanceState == VehicleMaintenanceState.JACKED)
            jackAnchor.ifPresent(anchor -> lowerFromJack(player, anchor, false));
    }

    private void returnStoredJack(Player player) {
        if (!jackItemStored) {
            return;
        }
        ItemStack returnedJack = new ItemStack(ModItems.PORTABLE_JACK.get());
        if (!player.addItem(returnedJack)) {
            player.drop(returnedJack, false);
        }
        jackItemStored = false;
    }

    private boolean applyPaint(Player player, ItemStack held, VehiclePaintItem paintItem) {
        if (!authorizeLockedVehicleOperation(player)) {
            return true;
        }
        if (maintenanceState == VehicleMaintenanceState.CARRIED
                || maintenanceState == VehicleMaintenanceState.DISASSEMBLING
                || maintenanceState == VehicleMaintenanceState.REMOVED) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.paint.invalid_state"), true);
            return true;
        }
        ResourceLocation paintId = paintItem.paint().id();
        if (paintId.equals(vehicleData.paintId())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.paint.already_applied"), true);
            return true;
        }
        Pathway.either(vehicleData.withPaint(definition(), paintId))
                .peekLeft(error -> player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.paint.unsupported"), true))
                .peek(updated -> {
                    applyVehicleData(updated, true);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    player.displayClientMessage(Component.translatable(
                            "message.polaris_vehicle.paint.applied"), true);
                });
        return true;
    }

    private boolean handleFuelingInteraction(Player player, InteractionHand hand) {
        boolean allowedState = maintenanceState == VehicleMaintenanceState.NORMAL
                || maintenanceState == VehicleMaintenanceState.JACKED
                || maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE;
        if (!allowedState
                || horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.fueling.vehicle_busy"), true);
            return true;
        }
        if (vehicleData.fuel() >= definition().fuelCapacity()) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.fueling.full"), true);
            return true;
        }
        if (fuelingPlayerUuid != null
                && fuelingPlayerUuid.equals(player.getUUID())
                && fuelingHand == hand) {
            if (!player.isUsingItem()) {
                player.startUsingItem(hand);
            }
            return true;
        }
        if (fuelingPlayerUuid != null && !fuelingPlayerUuid.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.fueling.in_use"), true);
            return true;
        }
        if (maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE
                && (assemblyTablePos == null
                || !(level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table)
                || !table.authorizePartTransfer(player, this))) {
            return true;
        }
        VehicleFuelTankItem tankItem = (VehicleFuelTankItem) player.getItemInHand(hand).getItem();
        if (tankItem.readData(player.getItemInHand(hand)).amountMl() <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.fueling.empty_tank"), true);
            return true;
        }
        fuelingPlayerUuid = player.getUUID();
        fuelingHand = hand;
        nextFuelTransferGameTime = level().getGameTime() + FUEL_TRANSFER_INTERVAL_TICKS;
        player.startUsingItem(hand);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.fueling.started"), true);
        return true;
    }

    public final void tryStartFuelingFromTargetRequest(ServerPlayer player) {
        if (level().isClientSide()
                || player.level() != level()
                || !player.isAlive()
                || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof VehicleFuelTankItem)) {
            return;
        }
        boolean aimedAtPort = isFuelPortAimedAt(player);
        boolean unobstructed = aimedAtPort && isFuelPortAimUnobstructed(player);
        if (!aimedAtPort || !unobstructed) {
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.fueling.aim_at_port"), true);
            return;
        }
        handleFuelingInteraction(player, InteractionHand.MAIN_HAND);
    }

    private boolean isFuelPortHit(Vec3 worldHitPosition) {
        return fuelPortWorldPosition()
                .filter(port -> port.distanceToSqr(worldHitPosition)
                        <= FUEL_PORT_TARGET_RADIUS * FUEL_PORT_TARGET_RADIUS)
                .isDefined();
    }

    private boolean isFuelPortAimedAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        double range = player.entityInteractionRange() + 1.0;
        return fuelPortWorldPosition().filter(port -> {
            double alongRay = Math.clamp(port.subtract(eye).dot(look), 0.0, range);
            Vec3 nearest = eye.add(look.scale(alongRay));
            return nearest.distanceToSqr(port)
                    <= FUEL_PORT_TARGET_RADIUS * FUEL_PORT_TARGET_RADIUS;
        }).isDefined();
    }

    private boolean isFuelPortAimUnobstructed(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        double range = player.entityInteractionRange() + 1.0;
        return fuelPortWorldPosition().filter(port -> {
            double portAlongRay = port.subtract(eye).dot(look);
            if (portAlongRay < 0.0 || portAlongRay > range) {
                return false;
            }
            BlockHitResult blockHit = level().clip(new ClipContext(
                    eye,
                    eye.add(look.scale(range)),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            return blockHit.getType() != HitResult.Type.BLOCK
                    || blockHit.getLocation().distanceTo(eye)
                    >= portAlongRay - FUEL_PORT_TARGET_RADIUS;
        }).isDefined();
    }

    private Maybe<Vec3> fuelPortWorldPosition() {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .flatMap(model -> model.locator("oil"))
                .map(local -> {
                    Vector3f rotated = vehicleBodyRotation().transform(new Vector3f(
                            (float) local.x,
                            (float) local.y,
                            (float) local.z
                    ));
                    return position().add(rotated.x, rotated.y, rotated.z);
                });
    }

    private void tickFuelingSession() {
        if (!(level() instanceof ServerLevel serverLevel)
                || fuelingPlayerUuid == null
                || fuelingHand == null) {
            return;
        }
        Player player = serverLevel.getPlayerByUUID(fuelingPlayerUuid);
        if (player == null
                || !player.isAlive()
                || player.distanceToSqr(this) > 36.0
                || !player.isUsingItem()
                || player.getUsedItemHand() != fuelingHand
                || !(player.getItemInHand(fuelingHand).getItem() instanceof VehicleFuelTankItem tankItem)) {
            clearFuelingSession();
            return;
        }
        boolean allowedState = maintenanceState == VehicleMaintenanceState.NORMAL
                || maintenanceState == VehicleMaintenanceState.JACKED
                || maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE;
        if (!allowedState
                || horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) {
            clearFuelingSession();
            return;
        }
        if (serverLevel.getGameTime() < nextFuelTransferGameTime) {
            return;
        }
        if (maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE
                && (assemblyTablePos == null
                || !(level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table)
                || !table.authorizePartTransfer(player, this))) {
            clearFuelingSession();
            return;
        }
        ItemStack tankStack = player.getItemInHand(fuelingHand);
        VehicleFuelTransferResult result = VehicleFuelService.transferToVehicle(
                definition(),
                vehicleData,
                tankItem.readData(tankStack),
                tankItem.capacityMl(),
                tankItem.transferPerIntervalMl()
        );
        if (result.transferredMl() <= 0) {
            clearFuelingSession();
            return;
        }
        applyVehicleData(result.vehicleData(), true);
        tankItem.writeData(tankStack, result.tankData());
        nextFuelTransferGameTime = serverLevel.getGameTime() + FUEL_TRANSFER_INTERVAL_TICKS;
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.fueling.progress",
                result.transferredMl(),
                vehicleData.fuel(),
                definition().fuelCapacity()
        ), true);
        if (vehicleData.fuel() >= definition().fuelCapacity() || result.tankData().amountMl() <= 0) {
            clearFuelingSession();
        }
    }

    private void clearFuelingSession() {
        if (level() instanceof ServerLevel serverLevel && fuelingPlayerUuid != null) {
            Player player = serverLevel.getPlayerByUUID(fuelingPlayerUuid);
            if (player != null && player.isUsingItem()) {
                player.stopUsingItem();
            }
        }
        fuelingPlayerUuid = null;
        fuelingHand = null;
        nextFuelTransferGameTime = 0L;
    }

    private boolean handleCarryInteraction(Player player) {
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return false;
        }
        if (VehicleMaintenanceControls.liftsVehicle(
                maintenanceState, true, player.isSecondaryUseActive())) {
            if (!authorizeLockedVehicleOperation(player)) {
                return true;
            }
            if (!getPassengers().isEmpty()
                    || horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.carry.vehicle_busy"), true);
                return true;
            }
            if (alreadyCarryingAnotherVehicle(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.carry.already_carrying"), true);
                return true;
            }
            if (vehicleData.engineRunning()) {
                updateVehicleData(data -> data.withEngineRunning(definition(), false), false);
            }
            clearJackAnchor();
            beginCarrying(player);
            return true;
        }
        if (maintenanceState == VehicleMaintenanceState.CARRIED
                && isCarriedBy(player)
                && !player.isSecondaryUseActive()) {
            if (!tryPlaceCarriedVehicle(player, carriedGroundPlacement(player))) {
                player.displayClientMessage(Component.translatable(
                        "message.polaris_vehicle.carry.blocked"), true);
                return true;
            }
            player.swing(InteractionHand.MAIN_HAND, true);
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.carry.placed"), true);
            return true;
        }
        if (maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE
                && player.isSecondaryUseActive()
                && assemblyTablePos != null) {
            if (level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table) {
                return table.takeVehicleForCarry(player, this);
            }
            player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.assembly_table.invalid_binding"), true);
            return true;
        }
        return maintenanceState == VehicleMaintenanceState.CARRIED;
    }

    private boolean alreadyCarryingAnotherVehicle(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var carried = VehicleCarryTracker.tracked(serverPlayer);
            if (carried != null && carried != this && !carried.isRemoved() && carried.isCarriedBy(player)) return true;
        }
        return !level().getEntitiesOfClass(
                AbstractVehicleEntity.class,
                player.getBoundingBox().inflate(8.0),
                vehicle -> vehicle != this && vehicle.isCarriedBy(player)
        ).isEmpty();
    }

    private void beginCarrying(Player player) {
        VehiclePhysicsManager.removeVehicle(this);
        setDeltaMovement(Vec3.ZERO);
        carrierUuid = player.getUUID();
        entityData.set(CARRIER_ENTITY_ID, player.getId());
        // The first carried pose is a discrete maintenance transfer. Send its position and
        // heading together before unlocking the fixed table/jack pose; otherwise clients can
        // receive the new BODY_ROTATION first and visibly rotate the vehicle on the table,
        // then receive the ordinary movement update that raises it to the player's hands.
        followCarrier(player, true);
        setMaintenanceState(VehicleMaintenanceState.CARRIED, true);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.carry.raised"), true);
    }

    private Vec3 carriedGroundPlacement(Player carrier) {
        Vec3 look = carrier.getViewVector(1.0F);
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-7) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 ahead = carrier.position().add(horizontal.scale(2.0));
        BlockHitResult ground = level().clip(new ClipContext(
                ahead.add(0.0, 2.0, 0.0),
                ahead.subtract(0.0, 4.0, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                carrier
        ));
        return ground.getType() == HitResult.Type.BLOCK
                ? ground.getLocation()
                : new Vec3(ahead.x, carrier.getY(), ahead.z);
    }

    private boolean canOccupy(AABB bounds, Entity ignoredEntity) {
        if (level().getBlockCollisions(this, bounds).iterator().hasNext()) {
            return false;
        }
        return level()
                .getEntities(
                        this,
                        bounds,
                        EntitySelector.NO_SPECTATORS
                                .and(this::canCollideWith)
                                .and(entity -> entity != ignoredEntity)
                )
                .isEmpty()
                && level().getWorldBorder().isWithinBounds(bounds);
    }

    private AABB placementCollisionBounds(Vec3 target) {
        AABB bounds = VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> model.physicsWorldBounds(target, getYRot()))
                .orElseGet(() -> getBoundingBox().move(target.subtract(position())));
        return bounds.deflate(1.0E-4);
    }

    public final boolean carryFromAssemblyTable(BlockPos tablePos, Player player) {
        if (level().isClientSide()
                || maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE
                || !Objects.equals(assemblyTablePos, tablePos)
                || !player.isSecondaryUseActive()
                || !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                || alreadyCarryingAnotherVehicle(player)) {
            return false;
        }
        if (!authorizeLockedVehicleOperation(player)) {
            return false;
        }
        assemblyTablePos = null;
        assemblyOwnerUuid = null;
        clearJackAnchor();
        beginCarrying(player);
        return true;
    }

    public final boolean tryPlaceCarriedVehicle(Player player, Vec3 anchor) {
        if (level().isClientSide() || !isCarriedBy(player)) {
            return false;
        }
        Vec3 target = groundedVehiclePosition(anchor);
        AABB placementBounds = placementCollisionBounds(target);
        if (!canOccupy(placementBounds, player)) {
            return false;
        }
        return Pathway.maybe(safePlayerPositionForVehicle(player, target))
                .peek(safePosition -> {
                    setMaintenancePosition(target);
                    movePlayerToSafePosition(player, safePosition);
                    carrierUuid = null;
                    entityData.set(CARRIER_ENTITY_ID, -1);
                    clearJackAnchor();
                    setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
                    placementProtectionTicks = PLACEMENT_PROTECTION_TICKS;
                })
                .map(ignored -> true)
                .getOrElse(false);
    }

    private void tickCarriedState() {
        if (!(level() instanceof ServerLevel serverLevel) || carrierUuid == null) {
            releaseCarriedState();
            return;
        }
        Player carrier = serverLevel.getServer().getPlayerList().getPlayer(carrierUuid);
        if (carrier == null || !carrier.isAlive() || carrier.isRemoved()) {
            releaseCarriedState();
            return;
        }
        if (carrier.level() == level()) {
            entityData.set(CARRIER_ENTITY_ID, carrier.getId());
            followCarrier(carrier, false);
            VehicleCarryTracker.refreshTicket(this);
        }
    }

    public final void synchronizeCarriedTo(ServerPlayer carrier) {
        if (!isCarriedBy(carrier)) return;
        if (!carrier.isAlive() || carrier.isRemoved()) {
            releaseCarriedState();
            return;
        }
        if (level() != carrier.serverLevel()) {
            Vec3 target = carriedPosition(carrier, carrier.position(), carrier.tickCount);
            // Entity.changeDimension does not load the destination chunk for
            // non-player entities. Prepare it before removing the source and
            // use the standard teleport ticket until the carry ticket takes over.
            carrier.serverLevel().getChunkAt(BlockPos.containing(target));
            var moved = changeDimension(new DimensionTransition(
                    carrier.serverLevel(), target,
                    Vec3.ZERO, carrier.getYRot(), 0,
                    DimensionTransition.PLACE_PORTAL_TICKET));
            if (moved instanceof AbstractVehicleEntity vehicle) {
                VehicleCarryTracker.untrack(this);
                vehicle.entityData.set(CARRIER_ENTITY_ID, carrier.getId());
                vehicle.followCarrier(carrier, true);
                VehicleCarryTracker.track(vehicle, carrier.getUUID());
            }
            return;
        }
        // Far jumps need an atomic pose update, ordinary movement retains the
        // render-time hand attachment. Chunk tickets keep the source alive until this runs.
        boolean jumped = distanceToSqr(carrier) > 64.0;
        entityData.set(CARRIER_ENTITY_ID, carrier.getId());
        followCarrier(carrier, jumped);
        VehicleCarryTracker.refreshTicket(this);
    }

    private void followCarrier(Player carrier, boolean initialTransfer) {
        Vec3 target = carriedPosition(carrier, carrier.position(), carrier.tickCount);
        setYRot(carrier.getYRot());
        setYBodyRot(carrier.getYRot());
        synchronizePoseRotation();
        if (initialTransfer) {
            setMaintenancePosition(target);
        } else {
            setPos(target);
        }
    }

    private Vec3 carriedPosition(Entity carrier, Vec3 carrierPosition, double carrierTick) {
        double animationSeconds = carrierTick / 20.0;
        double handWave = Math.cos(Math.toRadians(animationSeconds * 150.0 + 20.0))
                * (0.5 / 16.0);
        double handTop = carrierPosition.y
                + carrier.getBbHeight()
                + CARRY_HAND_TOP_ABOVE_PLAYER
                + handWave;
        return new Vec3(
                carrierPosition.x,
                handTop - visibleVehicleBottomOffset(),
                carrierPosition.z
        );
    }

    final void releaseCarriedState() {
        if (maintenanceState != VehicleMaintenanceState.CARRIED) {
            return;
        }
        carrierUuid = null;
        entityData.set(CARRIER_ENTITY_ID, -1);
        clearJackAnchor();
        setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
    }

    private boolean handleAssemblyBodyInteraction(Player player) {
        if (maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE
                || assemblyTablePos == null
                || player.distanceToSqr(this) > 36.0) {
            return false;
        }
        if (!(level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table)) {
            return true;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof VehicleWrenchItem wrench) || wrench.electric()) {
            return false;
        }
        playToolSound(
                ModSounds.WRENCH_MANUAL.get(),
                0.7F,
                1.0F
        );
        table.openAssemblySession(player, this);
        return true;
    }

    private void playToolSound(SoundEvent sound, float volume, float pitch) {
        level().playSound(
                null,
                blockPosition(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private void validateAssemblyTableBinding() {
        if (assemblyTablePos == null) {
            releaseOrphanedAssemblyState();
            return;
        }
        if (assemblyValidationGraceTicks > 0) {
            assemblyValidationGraceTicks--;
        }
        if (!(level().getBlockEntity(assemblyTablePos) instanceof AssemblyTableBlockEntity table)) {
            if (assemblyValidationGraceTicks == 0) {
                releaseOrphanedAssemblyState();
            }
            return;
        }
        if (!table.recoverVehicleBinding(this, assemblyOwnerUuid)) {
            releaseOrphanedAssemblyState();
            return;
        }
        double supportedY = assemblyTablePos.getY() + AssemblyTableBlock.SUPPORT_HEIGHT - chassisBottomOffset();
        if (Math.abs(getY() - supportedY) > 1.0E-6) {
            setMaintenancePosition(new Vec3(getX(), supportedY, getZ()));
        }
    }

    private void releaseOrphanedAssemblyState() {
        assemblyTablePos = null;
        assemblyOwnerUuid = null;
        carrierUuid = null;
        entityData.set(CARRIER_ENTITY_ID, -1);
        clearJackAnchor();
        setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
    }

    private boolean canOccupy(AABB bounds) {
        if (level().getBlockCollisions(this, bounds).iterator().hasNext()) {
            return false;
        }
        return level().getEntityCollisions(this, bounds).isEmpty()
                && level().getWorldBorder().isWithinBounds(bounds);
    }

    private Vec3 jackSupportedPosition(Vec3 anchor) {
        Vec3 horizontalOffset = chassisBottomCenterHorizontalOffset();
        return new Vec3(
                anchor.x - horizontalOffset.x,
                anchor.y + JACK_SUPPORT_HEIGHT - chassisBottomOffset(),
                anchor.z - horizontalOffset.z
        );
    }

    private Vec3 groundedVehiclePosition(Vec3 anchor) {
        Vec3 horizontalOffset = chassisBottomCenterHorizontalOffset();
        return new Vec3(
                anchor.x - horizontalOffset.x,
                anchor.y - groundContactOffset(),
                anchor.z - horizontalOffset.z
        );
    }

    private Vec3 groundedJackBase(Vec3 vehicleOrigin) {
        Vec3 horizontalOffset = chassisBottomCenterHorizontalOffset();
        return new Vec3(
                vehicleOrigin.x + horizontalOffset.x,
                vehicleOrigin.y + groundContactOffset(),
                vehicleOrigin.z + horizontalOffset.z
        );
    }

    private Vec3 chassisBottomCenterHorizontalOffset() {
        Vec3 localCenter = VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> {
                    AABB bounds = model.physicsLocalBounds();
                    return new Vec3(
                            (bounds.minX + bounds.maxX) * 0.5,
                            0.0,
                            (bounds.minZ + bounds.maxZ) * 0.5
                    );
                })
                .orElse(Vec3.ZERO);
        return localCenter.yRot((float) -Math.toRadians(getYRot()));
    }

    private double chassisBottomOffset() {
        return VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> model.physicsLocalBounds().minY)
                .orElse(0.0);
    }

    private double visibleVehicleBottomOffset() {
        double modelBottom = VehicleStructureModelManager.forLevel(level())
                .find(vehicleTypeId())
                .map(model -> model.physicsLocalBounds().minY)
                .orElse(chassisBottomOffset());
        return Math.min(modelBottom, installedWheelBottomOffset());
    }

    private double groundContactOffset() {
        boolean hasInstalledWheel = definition().wheelSlots().stream()
                .anyMatch(slot -> vehicleData.part(slot).isDefined());
        return hasInstalledWheel
                ? Math.min(chassisBottomOffset(), installedWheelBottomOffset())
                : chassisBottomOffset();
    }

    private double installedWheelBottomOffset() {
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicleTypeId());
        return profile.wheels().stream()
                .filter(mount -> vehicleData.part(mount.slot()).isDefined())
                .mapToDouble(mount -> mount.wheelCenter().y
                        - VehicleWheelContactGeometry.radius(profile, vehicleData, mount))
                .min()
                .orElse(chassisBottomOffset());
    }

    private AABB jackSupportedBounds(Vec3 anchor) {
        return getBoundingBox().move(jackSupportedPosition(anchor).subtract(position()));
    }

    private void setJackAnchor(Vec3 anchor) {
        jackAnchor = Maybe.some(anchor);
        entityData.set(JACK_ANCHOR_X_BITS, Double.doubleToRawLongBits(anchor.x));
        entityData.set(JACK_ANCHOR_Y_BITS, Double.doubleToRawLongBits(anchor.y));
        entityData.set(JACK_ANCHOR_Z_BITS, Double.doubleToRawLongBits(anchor.z));
        entityData.set(JACK_ANCHOR_PRESENT, true);
    }

    private void clearJackAnchor() {
        jackAnchor = Maybe.none();
        entityData.set(JACK_ANCHOR_PRESENT, false);
        entityData.set(JACK_LIFT_START_TICK, Long.MIN_VALUE);
        entityData.set(JACK_LOWERING, false);
        jackLoweringPlayer = null;
    }

    public final double jackLiftAnimationTick(float partialTick) {
        long start = entityData.get(JACK_LIFT_START_TICK);
        return start == Long.MIN_VALUE ? Double.POSITIVE_INFINITY
                : VehicleJackAnimationTiming.tick(level().getGameTime() - start + partialTick,
                entityData.get(JACK_LOWERING));
    }

    private void setMaintenancePosition(Vec3 target) {
        if (level().isClientSide()) {
            throw new IllegalStateException("Maintenance transforms are server-authoritative");
        }
        setPos(target);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
        new VehicleMaintenanceTransformPacket(
                getId(),
                target.x,
                target.y,
                target.z,
                getYRot(),
                getXRot()
        ).sendToTrackingEntity(this);
    }

    private void resendMaintenanceTransform() {
        new VehicleMaintenanceTransformPacket(
                getId(), getX(), getY(), getZ(), getYRot(), getXRot()
        ).sendToTrackingEntity(this);
    }

    private void setMaintenanceState(VehicleMaintenanceState state, boolean synchronize) {
        if (level().isClientSide()) {
            throw new IllegalStateException("Vehicle maintenance state is server-authoritative");
        }
        maintenanceState = Objects.requireNonNull(state, "state");
        if (state != VehicleMaintenanceState.CARRIED) VehicleCarryTracker.untrack(this);
        else if (isAddedToLevel()) VehicleCarryTracker.track(this, carrierUuid);
        entityData.set(MAINTENANCE_STATE, maintenanceState.ordinal());
        if (!level().isClientSide() && maintenanceState != VehicleMaintenanceState.NORMAL) {
            resetSuspensionCompression();
        }

        if (synchronize && !level().isClientSide()) {
            hasImpulse = true;
        }
    }

    private ItemStack stackForPart(InstalledPart part) {
        Item item = BuiltInRegistries.ITEM.get(part.partId());
        if (!(item instanceof VehiclePartItem partItem)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        partItem.writePart(stack, part);
        return stack;
    }

    private Maybe<VehiclePartSlot> findWheelSlot(
            Player player,
            boolean allowFallback
    ) {
        Vec3 eyeWorld = player.getEyePosition(1.0F);
        Vec3 viewWorld = player.getViewVector(1.0F).normalize();
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicleTypeId());
        var model = VehicleStructureModelManager.forLevel(level()).find(vehicleTypeId());
        if (model.isEmpty()) return Maybe.none();
        List<VehicleWheelSelector.Target> candidates =
                VehicleWheelInteractionRegions.targets(profile, model.get());
        Maybe<VehicleWheelSelector.Target> selected = VehicleWheelSelector.selectWorld(
                eyeWorld,
                viewWorld,
                position(),
                vehicleBodyRotation(),
                player.entityInteractionRange(),
                profile.tireRadius(),
                candidates,
                allowFallback
        );
        selected = selected.filter(target -> {
            Vec3 center = VehicleSeatTransform.toBodySpace(target.center(), vehicleBodyRotation())
                    .add(position());
            var block = level().clip(new ClipContext(eyeWorld, center, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            return block.getType() == HitResult.Type.MISS
                    || block.getLocation().distanceTo(center) < profile.tireRadius() + 0.15;
        });
        return selected.map(target -> profile.wheels()
                        .get(target.wheelIndex())
                        .slot())
                .map(VehicleWheelSelector::interactionSlot);
    }

    public final boolean hasWheelInteractionTarget(Player player) {
        return findWheelSlot(player, false).isDefined();
    }

    public final void handleWheelTargetRequest(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (player.level() != level() || !isAlive() || player.getVehicle() != null
                || !(held.isEmpty() || held.getItem() instanceof VehicleTireItem
                || held.getItem() instanceof VehicleWrenchItem)
                || !hasWheelInteractionTarget(player)) return;
        interact(player, InteractionHand.MAIN_HAND);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity passenger : getPassengers()) {
            if (seatIndexFor(passenger) == 0 && passenger instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (!maintenanceState().allowsBoarding() || !(passenger instanceof LivingEntity)) {
            return false;
        }
        return seatAssignments.seatFor(passenger.getUUID()).fold(
                () -> firstAvailableSeat(passenger) >= 0,
                seat -> seat >= 0
                        && seat < seatLayout().seats().size()
                        && !isSeatOccupied(seat, passenger)
        );
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        int assignedSeat = seatAssignments.seatFor(passenger.getUUID()).fold(
                () -> firstAvailableSeat(passenger),
                seat -> seat
        );
        if (assignedSeat < 0 || assignedSeat >= seatLayout().seats().size()) {
            assignedSeat = Math.clamp(getPassengers().indexOf(passenger), 0, seatLayout().seats().size() - 1);
        }
        VehicleSeatAssignments updated = seatAssignments.assign(passenger.getUUID(), assignedSeat);
        if (level().isClientSide()) {
            seatAssignments = updated;
        } else {
            setSeatAssignments(updated);
        }
    }

    public final void acceptDismountPress(ServerPlayer player) {
        if (level().isClientSide() || player.getVehicle() != this || !hasPassenger(player)) return;
        switch (dismountGesture.press(player.getUUID(), level().getGameTime())) {
            case FIRST_PRESS -> player.displayClientMessage(Component.translatable(
                    "message.polaris_vehicle.dismount.press_again",
                    Component.keybind("key.sneak")), true);
            case DISMOUNT -> {
                player.stopRiding();
                player.setShiftKeyDown(false);
            }
            case NONE -> {
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        dismountGesture.forget(passenger.getUUID());
        VehicleSeatAssignments updated = seatAssignments.without(passenger.getUUID());
        if (level().isClientSide()) {
            seatAssignments = updated;
        } else {
            setSeatAssignments(updated);
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(
            Entity passenger,
            EntityDimensions dimensions,
            float scale
    ) {
        Vec3 localSeat = dimensions.attachments().getClamped(
                EntityAttachment.PASSENGER,
                seatIndexFor(passenger),
                0.0F
        );
        // The chassis renderer uses the complete Bullet orientation. Applying
        // only EntityAttachments' yaw rotation here leaves riders behind when
        // suspension pitches or rolls the visible seat. Rotate the local seat
        // by that same body quaternion; Entity.positionRider still subtracts
        // the passenger's own VEHICLE attachment afterwards.
        return VehicleSeatTransform.toBodySpace(localSeat, vehicleBodyRotation());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Quaternionf bodyRotation = vehicleBodyRotation();
        Vec3 localSeat = getDimensions(getPose()).attachments().getClamped(
                EntityAttachment.PASSENGER,
                seatIndexFor(passenger),
                0.0F
        );
        Vec3 localRiderAttachment = passenger.getAttachments().get(
                EntityAttachment.VEHICLE,
                0,
                0.0F
        );
        Vec3 riderRootOffset = VehicleSeatTransform.riderRootOffset(
                localSeat,
                localRiderAttachment,
                bodyRotation
        );
        callback.accept(
                passenger,
                getX() + riderRootOffset.x,
                getY() + riderRootOffset.y,
                getZ() + riderRootOffset.z
        );
        if (shouldRotatePassengerView(passenger)) {
            float vehicleYawDelta = Mth.wrapDegrees(getYRot() - yRotO);
            passenger.setYRot(passenger.getYRot() + vehicleYawDelta);
            passenger.setYHeadRot(passenger.getYHeadRot() + vehicleYawDelta);
        }
        clampPassengerRotation(passenger);
    }

    protected boolean shouldRotatePassengerView(Entity passenger) {
        return true;
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        clampPassengerRotation(passenger);
    }

    private void clampPassengerRotation(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        if (!shouldRotatePassengerView(passenger)) {
            return;
        }
        float relativeYaw = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clampedYaw = Math.clamp(relativeYaw, -105.0F, 105.0F);
        passenger.yRotO += clampedYaw - relativeYaw;
        passenger.setYRot(passenger.getYRot() + clampedYaw - relativeYaw);
        passenger.setYHeadRot(passenger.getYRot());
    }

    private VehicleSeatLayout seatLayout() {
        return VehicleSeatLayout.require(defaultDefinition.id());
    }

    private int seatIndexFor(Entity passenger) {
        return seatAssignments.seatFor(passenger.getUUID()).fold(
                () -> Math.max(0, getPassengers().indexOf(passenger)),
                seat -> Math.clamp(seat, 0, seatLayout().seats().size() - 1)
        );
    }

    private int firstAvailableSeat(Entity passengerToIgnore) {
        for (int seat = 0; seat < seatLayout().seats().size(); seat++) {
            if (!isSeatOccupied(seat, passengerToIgnore)) {
                return seat;
            }
        }
        return -1;
    }

    private boolean isSeatOccupied(int seat, Entity passengerToIgnore) {
        for (Entity passenger : getPassengers()) {
            if (passenger != passengerToIgnore && seatIndexFor(passenger) == seat) {
                return true;
            }
        }
        return false;
    }

    private Vec3 seatWorldVisibilityPoint(Player player, int seatIndex) {
        Vec3 localSeat = seatLayout().seats().get(seatIndex);
        Vec3 localRiderAttachment = player.getAttachments().get(
                EntityAttachment.VEHICLE,
                0,
                0.0F
        );
        Vec3 rotatedEye = VehicleSeatTransform.riderPointOffset(
                localSeat,
                localRiderAttachment,
                new Vec3(0.0, player.getEyeHeight(), 0.0),
                vehicleBodyRotation()
        );
        return position().add(rotatedEye);
    }


    public final Vec3 riderEyeRenderPosition(Player player, float partialTick) {
        Vec3 eye = riderEyeOffset(player, seatIndexFor(player), vehicleBodyRenderRotation(partialTick));
        return getPosition(partialTick).add(eye);
    }

    public final Vec3 riderEyeLocalOffset(Player player) {
        return riderEyeOffset(player, seatIndexFor(player), new Quaternionf());
    }

    public final Vec3 driverSeatEyeLocalOffset(Player player) {
        return riderEyeOffset(player, 0, new Quaternionf());
    }

    private Vec3 riderEyeOffset(Player player, int seat, Quaternionf rotation) {
        Vec3 localSeat = seatLayout().seats().get(seat);
        Vec3 attachment = player.getAttachments().get(EntityAttachment.VEHICLE, 0, 0);
        return VehicleSeatTransform.riderPointOffset(localSeat, attachment,
                new Vec3(0, player.getEyeHeight(), 0), rotation);
    }

    private boolean isSeatObstructed(Player player, Vec3 eyePosition, Vec3 seatPosition) {
        BlockHitResult hit = level().clip(new ClipContext(
                eyePosition,
                seatPosition,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        return hit.getType() != HitResult.Type.MISS
                && eyePosition.distanceToSqr(hit.getLocation()) + 0.01
                < eyePosition.distanceToSqr(seatPosition);
    }

    private void setSeatAssignments(VehicleSeatAssignments assignments) {
        if (level().isClientSide()) {
            throw new IllegalStateException("Seat assignments are server-authoritative");
        }
        seatAssignments = Objects.requireNonNull(assignments, "assignments");
        entityData.set(SEAT_ASSIGNMENTS, encodeSeatAssignments(assignments));
    }

    private static CompoundTag encodeSeatAssignments(VehicleSeatAssignments assignments) {
        return CodecUtil.toEither(VehicleSeatAssignments.CODEC.encodeStart(
                        NbtOps.INSTANCE,
                        assignments
                ))
                .fold(
                        message -> {
                            throw new IllegalStateException(
                                    "Failed to encode vehicle seat assignments: " + message
                            );
                        },
                        encoded -> {
                            if (encoded instanceof CompoundTag compoundTag) {
                                return compoundTag;
                            }
                            throw new IllegalStateException(
                                    "Vehicle seat assignments did not encode to a compound tag"
                            );
                        }
                );
    }

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        new VehicleDataSyncPacket(getId(), vehicleData).sendTo(player);
    }

    public final VehicleInstanceData vehicleData() {
        return vehicleData;
    }

    public final VehicleDefinition definition() {
        return VehicleDefinitions.find(vehicleData.vehicleType()).orElse(defaultDefinition);
    }

    public final boolean restoreFromItem(VehicleInstanceData data) {
        if (level().isClientSide()
                || data == null
                || !defaultDefinition.id().equals(data.vehicleType())) {
            return false;
        }
        applyVehicleData(data.sanitized(defaultDefinition), false);
        return true;
    }

    @Override
    public final AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public final void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "steering",
                STEERING_ANIMATION_TRANSITION_TICKS,
                state -> {
                    int steering = steeringInput();
                    if (steering < 0) {
                        return state.setAndContinue(turnLeftAnimation);
                    }
                    if (steering > 0) {
                        return state.setAndContinue(turnRightAnimation);
                    }
                    return PlayState.STOP;
                }
        ));
    }

    public final int steeringInput() {
        return entityData.get(STEERING_INPUT);
    }

    public final int throttleInput() {
        return entityData.get(THROTTLE_INPUT);
    }

    public final boolean brakeInput() {
        return entityData.get(BRAKE_INPUT);
    }

    public final boolean hornActive() {
        return entityData.get(HORN_ACTIVE);
    }

    public final double horizontalSpeedMetersPerSecond() {
        return getDeltaMovement().horizontalDistance() * 20.0;
    }

    public final double signedForwardSpeedMetersPerSecond() {
        Vec3 forward = horizontalLookDirection();
        if (level().isClientSide()) {
            return ((getX() - xo) * forward.x + (getZ() - zo) * forward.z) * 20.0;
        }
        Vec3 velocity = getDeltaMovement();
        return (velocity.x * forward.x + velocity.z * forward.z) * 20.0;
    }

    public final Vec3 horizontalForwardDirection() {
        return horizontalLookDirection();
    }

    double appliedSlipstreamBonus() {
        return appliedSlipstreamBonus;
    }

    double measuredSlipstreamBonus() {
        return measuredSlipstreamBonus;
    }

    double appliedMaxSpeed() {
        return appliedMaxSpeed;
    }

    private Vec3 rearBumper() {
        return position().subtract(horizontalForwardDirection().scale(longitudinalExtent(false)));
    }

    private double slipstreamMaxSpeedBonus() {
        VehicleSlipstreamProfile profile = VehicleSlipstreamProfile.DEFAULT;
        double reach = VehicleSlipstreamService.wakeReach(profile, SLIPSTREAM_LONGEST_LEADER);
        AABB searchBounds = getBoundingBox().inflate(reach + profile.halfWidth() * 2 + SLIPSTREAM_SEARCH_BODY_MARGIN);
        Vec3 nose = position().add(horizontalForwardDirection().scale(longitudinalExtent(true)));
        double bestBonus = 0.0;
        for (AbstractVehicleEntity front : level().getEntitiesOfClass(
                AbstractVehicleEntity.class,
                searchBounds,
                candidate -> candidate != this && !candidate.isRemoved()
        )) {
            double tier = profile.bonusForSpeed(front.horizontalSpeedMetersPerSecond());
            if (tier <= bestBonus) {
                continue;
            }
            List<Vec3> trail = front.slipstreamTrail.snapshot(front.rearBumper());
            double strength = VehicleSlipstreamService.wakeStrength(
                    profile,
                    trail,
                    front.horizontalForwardDirection(),
                    front.longitudinalExtent(true) + front.longitudinalExtent(false),
                    nose,
                    horizontalForwardDirection()
            );
            bestBonus = Math.max(bestBonus, Math.round(tier * strength * 20.0) / 20.0);
        }
        return bestBonus;
    }

    private double longitudinalExtent(boolean front) {
        return VehicleStructureModelManager.forLevel(level()).find(vehicleTypeId()).fold(
                () -> 0.0,
                model -> Math.max(0.0, front
                        ? model.physicsLocalBounds().maxZ
                        : -model.physicsLocalBounds().minZ)
        );
    }

    private void tickClientWheelRotation() {
        clientWheelRotationOld = clientWheelRotation;
        if (!isEngineRunning()) {
            return;
        }
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicleTypeId());
        double distanceThisTick = signedForwardSpeedMetersPerSecond() / 20.0;
        // GeckoLib converts the supplied positive Bedrock X rotation to a
        // negative model-space angle.
        clientWheelRotation -= distanceThisTick
                / VehicleWheelContactGeometry.averageInstalledRadius(profile, vehicleData);
        double fullTurn = Math.PI * 2.0;
        if (Math.abs(clientWheelRotation) > fullTurn * 1024.0) {
            double completedTurns = Math.floor(clientWheelRotation / fullTurn) * fullTurn;
            clientWheelRotationOld -= completedTurns;
            clientWheelRotation -= completedTurns;
        }
    }

    private void tickClientSteeringAngle() {
        clientSteeringTimeline.accept(entityData.get(ACTUAL_STEERING), System.nanoTime() * 1E-9);
    }

    private void tickClientSuspensionCompression() {
        for (int index = 0; index < clientSuspensionCompression.length; index++) {
            clientSuspensionCompressionOld[index] = clientSuspensionCompression[index];
            clientSuspensionCompression[index] = entityData.get(suspensionCompressionAccessor(index));
        }
    }

    private void updateSuspensionCompression(VehiclePhysicsSnapshot snapshot) {
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicleTypeId());
        int count = Math.min(profile.wheels().size(), snapshot.suspensionLengths().size());
        for (int index = 0; index < 4; index++) {
            float compression = 0.0F;
            if (index < count) {
                double raw = profile.suspensionRestLength()
                        - snapshot.suspensionLengths().get(index);
                double clamped = Math.clamp(
                        raw,
                        -profile.suspensionMaxDroop(),
                        profile.suspensionRestLength()
                );
                compression = Math.round((float) clamped * SUSPENSION_COMPRESSION_QUANTIZATION)
                        / SUSPENSION_COMPRESSION_QUANTIZATION;
            }
            entityData.set(suspensionCompressionAccessor(index), compression);
        }
    }

    private void resetSuspensionCompression() {
        for (int index = 0; index < 4; index++) {
            entityData.set(suspensionCompressionAccessor(index), 0.0F);
        }
        latestPhysicalWheelContactPoints = List.of();
        latestPhysicalWheelContacts = List.of();
        latestPhysicalWheelContactGameTime = Long.MIN_VALUE;
    }

    private static EntityDataAccessor<Float> suspensionCompressionAccessor(int index) {
        return switch (index) {
            case 0 -> SUSPENSION_COMPRESSION_0;
            case 1 -> SUSPENSION_COMPRESSION_1;
            case 2 -> SUSPENSION_COMPRESSION_2;
            case 3 -> SUSPENSION_COMPRESSION_3;
            default -> throw new IndexOutOfBoundsException("Suspension index " + index);
        };
    }

    public final float wheelRenderRotation(float partialTick) {
        return (float) Mth.lerp(
                Math.clamp(partialTick, 0.0F, 1.0F),
                clientWheelRotationOld,
                clientWheelRotation
        );
    }

    public final float steeringRenderAngle(float partialTick) {
        return (float) clientSteeringTimeline.sample(System.nanoTime() * 1E-9);
    }

    public final float steeringRenderProgress(float partialTick) {
        return VehicleTireSteeringLimits.INSTANCE
                .visualProgress(vehicleTypeId(), steeringRenderAngle(partialTick));
    }

    public final float wheelSuspensionRenderOffset(
            VehiclePartSlot slot,
            float partialTick
    ) {
        List<VehicleWheelMount> wheels = VehiclePhysicsProfile.require(vehicleTypeId()).wheels();
        for (int index = 0; index < wheels.size(); index++) {
            if (wheels.get(index).slot() != slot) {
                continue;
            }
            float compression = Mth.lerp(
                    Math.clamp(partialTick, 0.0F, 1.0F),
                    clientSuspensionCompressionOld[index],
                    clientSuspensionCompression[index]
            );
            // GeckoLib translates positive bone Y upward and uses 16 model pixels per block.
            return compression * 16.0F;
        }
        return 0.0F;
    }

    public final Maybe<Vec3> physicalWheelContactPoint(int wheelIndex) {
        if (level().isClientSide()
                || wheelIndex < 0
                || wheelIndex >= latestPhysicalWheelContacts.size()
                || wheelIndex >= latestPhysicalWheelContactPoints.size()
                || !latestPhysicalWheelContacts.get(wheelIndex)
                || level().getGameTime() - latestPhysicalWheelContactGameTime > 2L) {
            return Maybe.none();
        }
        return Maybe.some(latestPhysicalWheelContactPoints.get(wheelIndex));
    }

    public final ResourceLocation vehicleTypeId() {
        return ResourceLocation.parse(entityData.get(VEHICLE_TYPE));
    }

    public final void markAsClientGuiPreview() {
        if (!level().isClientSide()) {
            throw new IllegalStateException("GUI preview vehicles may only exist on the client");
        }
        clientGuiPreview = true;
    }

    public final boolean isClientGuiPreview() {
        return clientGuiPreview;
    }

    public final int fuel() {
        return entityData.get(FUEL);
    }

    public final boolean isEngineRunning() {
        return entityData.get(ENGINE_RUNNING);
    }

    public final VehicleMaintenanceState maintenanceState() {
        if (level().isClientSide()) {
            int ordinal = entityData.get(MAINTENANCE_STATE);
            VehicleMaintenanceState[] values = VehicleMaintenanceState.values();
            return ordinal >= 0 && ordinal < values.length
                    ? values[ordinal]
                    : VehicleMaintenanceState.NORMAL;
        }
        return maintenanceState;
    }

    public final Maybe<Vec3> jackAnchorPosition() {
        if (!level().isClientSide()) {
            return jackAnchor;
        }
        if (!entityData.get(JACK_ANCHOR_PRESENT)) {
            return Maybe.none();
        }
        return Maybe.some(new Vec3(
                Double.longBitsToDouble(entityData.get(JACK_ANCHOR_X_BITS)),
                Double.longBitsToDouble(entityData.get(JACK_ANCHOR_Y_BITS)),
                Double.longBitsToDouble(entityData.get(JACK_ANCHOR_Z_BITS))
        ));
    }

    public final boolean isWheelInstalled(VehiclePartSlot slot) {
        EntityDataAccessor<Boolean> accessor = switch (slot) {
            case FRONT_LEFT_WHEEL -> FRONT_LEFT_WHEEL_INSTALLED;
            case FRONT_RIGHT_WHEEL -> FRONT_RIGHT_WHEEL_INSTALLED;
            case FRONT_CENTER_WHEEL -> FRONT_CENTER_WHEEL_INSTALLED;
            case REAR_LEFT_WHEEL -> REAR_LEFT_WHEEL_INSTALLED;
            case REAR_RIGHT_WHEEL -> REAR_RIGHT_WHEEL_INSTALLED;
            default -> throw new IllegalArgumentException("Not a wheel slot: " + slot);
        };
        return entityData.get(accessor);
    }

    public final int wheelTightening(VehiclePartSlot slot) {
        EntityDataAccessor<Integer> accessor = switch (slot) {
            case FRONT_LEFT_WHEEL -> FRONT_LEFT_WHEEL_TIGHTENING;
            case FRONT_RIGHT_WHEEL -> FRONT_RIGHT_WHEEL_TIGHTENING;
            case FRONT_CENTER_WHEEL -> FRONT_CENTER_WHEEL_TIGHTENING;
            case REAR_LEFT_WHEEL -> REAR_LEFT_WHEEL_TIGHTENING;
            case REAR_RIGHT_WHEEL -> REAR_RIGHT_WHEEL_TIGHTENING;
            default -> throw new IllegalArgumentException("Not a wheel slot: " + slot);
        };
        return Math.clamp(entityData.get(accessor), 0, 4);
    }

    public final boolean attachToAssemblyTable(BlockPos tablePos, UUID ownerUuid) {
        if (level().isClientSide()
                || maintenanceState != VehicleMaintenanceState.CARRIED
                || !ownerUuid.equals(carrierUuid)
                || !getPassengers().isEmpty()
                || horizontalSpeedMetersPerSecond() > MAINTENANCE_SPEED_EPSILON) {
            return false;
        }
        if (!bindToAssemblyTable(tablePos, ownerUuid)) {
            return false;
        }
        pushAssemblyOwnerOutsideVehicle(ownerUuid);
        return true;
    }

    public final boolean attachPlacedItemToAssemblyTable(BlockPos tablePos, UUID ownerUuid) {
        if (level().isClientSide()
                || maintenanceState != VehicleMaintenanceState.NORMAL
                || !getPassengers().isEmpty()) {
            return false;
        }
        return bindToAssemblyTable(tablePos, ownerUuid);
    }

    private boolean bindToAssemblyTable(BlockPos tablePos, UUID ownerUuid) {
        VehiclePhysicsManager.removeVehicle(this);
        setDeltaMovement(Vec3.ZERO);
        assemblyTablePos = tablePos.immutable();
        assemblyOwnerUuid = ownerUuid;
        carrierUuid = null;
        entityData.set(CARRIER_ENTITY_ID, -1);
        clearJackAnchor();
        assemblyValidationGraceTicks = 100;
        float alignedYaw = AssemblyTableBlock.alignedVehicleYaw(
                level().getBlockState(tablePos),
                getYRot()
        );
        setYRot(alignedYaw);
        setYBodyRot(alignedYaw);
        setXRot(0.0F);
        synchronizePoseRotation();
        Vec3 horizontalOffset = chassisBottomCenterHorizontalOffset();
        setMaintenancePosition(new Vec3(
                tablePos.getX() + 0.5 - horizontalOffset.x,
                tablePos.getY() + AssemblyTableBlock.SUPPORT_HEIGHT - chassisBottomOffset(),
                tablePos.getZ() + 0.5 - horizontalOffset.z
        ));
        setMaintenanceState(VehicleMaintenanceState.ASSEMBLY_TABLE, true);
        placementProtectionTicks = PLACEMENT_PROTECTION_TICKS;
        return true;
    }

    private void pushAssemblyOwnerOutsideVehicle(UUID ownerUuid) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player foundPlayer = serverLevel.getPlayerByUUID(ownerUuid);
        if (!(foundPlayer instanceof ServerPlayer player)) {
            return;
        }
        safePlayerPositionForVehicle(player, position())
                .ifPresent(safePosition -> movePlayerToSafePosition(player, safePosition));
    }

    private void tickPlacementProtection() {
        if (placementProtectionTicks <= 0) return;
        if (maintenanceState == VehicleMaintenanceState.CARRIED
                || maintenanceState == VehicleMaintenanceState.DISASSEMBLING
                || maintenanceState == VehicleMaintenanceState.REMOVED) {
            placementProtectionTicks = 0;
            return;
        }
        placementProtectionTicks--;
        AABB bounds = VehicleStructureModelManager.forLevel(level()).find(vehicleTypeId())
                .map(model -> model.physicsWorldBounds(position(), vehicleBodyRotation()))
                .orElseGet(this::getBoundingBox).minmax(getBoundingBox());
        for (Player player : level().getEntitiesOfClass(Player.class, bounds,
                candidate -> candidate.isAlive() && !candidate.isSpectator() && !candidate.noPhysics
                        && candidate.getRootVehicle() != this)) {
            Maybe<Vec3> safePosition = safePlayerPositionForVehicle(player, position());
            if (safePosition.isDefined()) {
                movePlayerToSafePosition(player, safePosition.get());
            } else {
                placementProtectionTicks = Math.max(placementProtectionTicks, 1);
            }
        }
    }

    private Maybe<Vec3> safePlayerPositionForVehicle(Player player, Vec3 target) {
        AABB entityBounds = getBoundingBox().move(target.subtract(position()));
        var model = VehicleStructureModelManager.forLevel(level()).find(vehicleTypeId());
        AABB physicsBounds = model
                .map(structure -> structure.physicsWorldBounds(target, vehicleBodyRotation()))
                .orElse(entityBounds);
        return VehiclePlayerPlacement.findSafePosition(
                player.position(), player.getBoundingBox(), physicsBounds.minmax(entityBounds),
                bounds -> entityBounds.intersects(bounds) || model.map(structure ->
                        structure.intersectsWorldAabb(target, vehicleBodyRotation(), bounds,
                                VehicleCollisionRole.PHYSICS.mask(), true)).orElse(false),
                bounds -> isSafePlayerEjection(player, bounds)
        );
    }

    private static void movePlayerToSafePosition(Player player, Vec3 safePosition) {
        if (safePosition.distanceToSqr(player.position()) > 1.0E-8) {
            player.teleportTo(safePosition.x, safePosition.y, safePosition.z);
            player.fallDistance = 0.0F;
        }
    }

    private boolean isSafePlayerEjection(Player player, AABB candidateBounds) {
        if (!level().getWorldBorder().isWithinBounds(candidateBounds)
                || level().getBlockCollisions(player, candidateBounds).iterator().hasNext()) {
            return false;
        }
        AABB footing = new AABB(candidateBounds.minX, candidateBounds.minY - 0.08,
                candidateBounds.minZ, candidateBounds.maxX, candidateBounds.minY,
                candidateBounds.maxZ);
        if (!level().getBlockCollisions(player, footing).iterator().hasNext()) {
            return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(candidateBounds.minX, candidateBounds.minY - 0.08, candidateBounds.minZ),
                BlockPos.containing(candidateBounds.maxX, candidateBounds.maxY, candidateBounds.maxZ))) {
            var state = level().getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE)
                    || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                    || state.is(Blocks.POWDER_SNOW)) {
                return false;
            }
        }
        if (!level().getEntitiesOfClass(AbstractVehicleEntity.class, candidateBounds.inflate(8.0),
                vehicle -> vehicle != this && !vehicle.isRemoved()
                        && vehicle.maintenanceState() != VehicleMaintenanceState.CARRIED
                        && vehicle.maintenanceState() != VehicleMaintenanceState.DISASSEMBLING
                        && vehicle.maintenanceState() != VehicleMaintenanceState.REMOVED
                        && vehicle.intersectsVehicleStructure(candidateBounds,
                        VehicleCollisionRole.PHYSICS.mask(), true)).isEmpty()) {
            return false;
        }
        return level().getEntities(
                player,
                candidateBounds,
                EntitySelector.NO_SPECTATORS.and(entity -> entity != this
                        && (entity.canBeCollidedWith() || entity.isPushable()))
        ).isEmpty();
    }

    public final boolean detachFromAssemblyTable(BlockPos tablePos) {
        if (level().isClientSide()
                || maintenanceState != VehicleMaintenanceState.ASSEMBLY_TABLE
                || distanceToSqr(
                tablePos.getX() + 0.5,
                tablePos.getY() + 1.0,
                tablePos.getZ() + 0.5
        ) > 1.0) {
            return false;
        }
        assemblyTablePos = null;
        assemblyOwnerUuid = null;
        carrierUuid = null;
        entityData.set(CARRIER_ENTITY_ID, -1);
        clearJackAnchor();
        setMaintenanceState(VehicleMaintenanceState.NORMAL, true);
        return true;
    }

    public final boolean isAttachedToAssemblyTable(BlockPos tablePos) {
        return maintenanceState == VehicleMaintenanceState.ASSEMBLY_TABLE
                && Objects.equals(assemblyTablePos, tablePos);
    }

    public final boolean isCarriedBy(Player player) {
        if (player == null || maintenanceState() != VehicleMaintenanceState.CARRIED) {
            return false;
        }
        return level().isClientSide()
                ? entityData.get(CARRIER_ENTITY_ID) == player.getId()
                : player.getUUID().equals(carrierUuid);
    }

    public final int energy() {
        return entityData.get(ENERGY);
    }

    public final void updateVehicleData(UnaryOperator<VehicleInstanceData> update) {
        updateVehicleData(update, true);
    }

    private void updateVehicleData(
            UnaryOperator<VehicleInstanceData> update,
            boolean synchronize
    ) {
        if (level().isClientSide()) {
            throw new IllegalStateException("Vehicle state may only be mutated on the logical server");
        }
        VehicleInstanceData updated = Objects.requireNonNull(
                update.apply(vehicleData),
                "updated vehicle data"
        );
        applyVehicleData(updated, synchronize);
    }

    public final void applyNetworkData(VehicleInstanceData data) {
        if (!level().isClientSide()) {
            return;
        }
        applyVehicleData(data, false);
    }

    public final boolean isDriver(Entity entity) {
        return entity != null && getControllingPassenger() == entity;
    }

    public final void acceptDriverInput(ServerPlayer driver, boolean forward, boolean backward,
                                        boolean left, boolean right, boolean brake) {
        acceptDriverInput(driver, forward, backward, left, right, brake,
                VehicleSteeringResponse.DEFAULT);
    }

    public final void acceptDriverInput(
            ServerPlayer driver,
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean brake,
            VehicleSteeringResponse steeringResponse
    ) {
        if (!isDriver(driver) || maintenanceState != VehicleMaintenanceState.NORMAL) {
            return;
        }
        inputForward = forward;
        inputBackward = backward;
        inputLeft = left;
        inputRight = right;
        inputBrake = brake;
        inputSteeringResponse = Objects.requireNonNull(steeringResponse);
        lastDriverInputGameTime = level().getGameTime();
        entityData.set(STEERING_INPUT, Integer.compare((right ? 1 : 0) - (left ? 1 : 0), 0));
        entityData.set(THROTTLE_INPUT, Integer.compare((forward ? 1 : 0) - (backward ? 1 : 0), 0));
        entityData.set(BRAKE_INPUT, brake || (forward && backward));
    }

    public final void acceptHorn(ServerPlayer driver, boolean pressed) {
        if (!isDriver(driver) || maintenanceState != VehicleMaintenanceState.NORMAL) {
            return;
        }
        entityData.set(HORN_ACTIVE, pressed);
    }

    public final void toggleEngine(ServerPlayer driver) {
        if (!isDriver(driver) || !maintenanceState.allowsEngineOperation()) {
            return;
        }
        long currentTick = level().getGameTime();
        if (!VehicleActionRateLimit.allows(
                currentTick,
                lastEngineToggleTick,
                VehicleActionRateLimit.ONE_SHOT_INTERVAL_TICKS
        )) {
            return;
        }
        lastEngineToggleTick = currentTick;
        if (vehicleData.engineRunning()) {
            updateVehicleData(data -> data
                    .withEngineRunning(definition(), false)
                    .withBoostState(
                            definition(),
                            data.energy(),
                            data.boostCooldownTicks(),
                            0
                    ));
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.engine_stopped"), true);
            return;
        }
        if (!authorizeLockedVehicleOperation(driver)) {
            return;
        }
        if (vehicleData.fuel() <= 0) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.engine.no_fuel"), true);
            return;
        }
        if (!vehicleData.hasUsableEngine()) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.engine.no_engine"), true);
            return;
        }
        if (maintenanceState != VehicleMaintenanceState.JACKED && !vehicleData.hasDriveableWheels(definition())) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.engine.wheels_not_ready"), true);
            return;
        }
        updateVehicleData(data -> data.withEngineRunning(definition(), true));
        driver.displayClientMessage(Component.translatable("message.polaris_vehicle.engine_started"), true);
    }

    private boolean authorizeLockedVehicleOperation(Player player) {
        if (level().isClientSide()) {
            return false;
        }
        if (vehicleOwnerUuid == null || vehicleOwnerUuid.equals(player.getUUID())) {
            return true;
        }
        String owner = vehicleOwnerName.isBlank() ? vehicleOwnerUuid.toString() : vehicleOwnerName;
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.key.locked", owner), false);
        return player.isCreative();
    }

    private boolean bindVehicleOwner(Player player, ItemStack keyStack) {
        if (vehicleOwnerUuid != null) {
            player.displayClientMessage(Component.translatable(
                    vehicleOwnerUuid.equals(player.getUUID())
                            ? "message.polaris_vehicle.key.already_yours"
                            : "message.polaris_vehicle.key.already_owned",
                    vehicleOwnerName.isBlank() ? vehicleOwnerUuid.toString() : vehicleOwnerName
            ), true);
            return false;
        }
        vehicleOwnerUuid = player.getUUID();
        vehicleOwnerName = player.getGameProfile().getName();
        if (!player.getAbilities().instabuild) {
            keyStack.shrink(1);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        player.displayClientMessage(Component.translatable(
                "message.polaris_vehicle.key.bound",
                vehicleOwnerName
        ), true);
        return true;
    }

    public final void requestBoost(ServerPlayer driver) {
        if (!isDriver(driver) || maintenanceState != VehicleMaintenanceState.NORMAL) {
            return;
        }
        long currentTick = level().getGameTime();
        if (!VehicleActionRateLimit.allows(
                currentTick,
                lastBoostRequestTick,
                VehicleActionRateLimit.ONE_SHOT_INTERVAL_TICKS
        )) {
            return;
        }
        lastBoostRequestTick = currentTick;
        Maybe<VehiclePowerDefinition> extraPower = vehicleData.part(VehiclePartSlot.EXTRA_POWER_UNIT)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(power -> power.matches(part) && power.isExtraPowerUnit()));
        if (extraPower.isEmpty()) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.boost.no_unit"), true);
            return;
        }
        if (!vehicleData.engineRunning() || vehicleData.fuel() <= 0) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.boost.engine_off"), true);
            return;
        }
        if (vehicleData.boostRemainingTicks() > 0 || vehicleData.boostCooldownTicks() > 0) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.boost.cooldown"), true);
            return;
        }
        VehiclePowerDefinition power = extraPower.get();
        int energyCost = power.energyCost();
        if (vehicleData.energy() < energyCost) {
            driver.displayClientMessage(Component.translatable("message.polaris_vehicle.boost.no_energy"), true);
            return;
        }
        updateVehicleData(data -> data.withBoostState(
                definition(),
                data.energy() - energyCost,
                power.cooldownTicks(),
                power.boostDurationTicks()
        ));
        driver.displayClientMessage(Component.translatable("message.polaris_vehicle.boost.activated"), true);
    }

    private void clearDriverInput() {
        inputForward = false;
        inputBackward = false;
        inputLeft = false;
        inputRight = false;
        inputBrake = false;
        entityData.set(STEERING_INPUT, 0);
        entityData.set(THROTTLE_INPUT, 0);
        entityData.set(BRAKE_INPUT, false);
        entityData.set(HORN_ACTIVE, false);
    }

    public final boolean acceptsPhysicsSnapshots() {
        return !level().isClientSide() && !isRemoved() && !maintenanceState.blocksDriving();
    }

    public final void applyPhysicsSnapshot(VehiclePhysicsSnapshot snapshot) {
        if (level().isClientSide()) {
            throw new IllegalStateException("Bullet snapshots may only mutate the logical server vehicle");
        }
        Vec3 modelOrigin = snapshot.modelOrigin();
        Vec3 physicalVelocity = snapshot.linearVelocity();
        if (!finite(modelOrigin) || !finite(physicalVelocity)) {
            throw new IllegalArgumentException("Bullet snapshot contains a non-finite transform");
        }
        Quaternionf rotation = sanitizedRotation(new Quaternionf(
                snapshot.rotationX(),
                snapshot.rotationY(),
                snapshot.rotationZ(),
                snapshot.rotationW()
        ));
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F));
        float wrappedYaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float continuousYaw = MathUtil.unwrapDegrees(getYRot(), wrappedYaw);
        float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(-forward.y, -1.0F, 1.0F)));

        setPos(modelOrigin);
        setDeltaMovement(
                physicalVelocity.x / 20.0,
                physicalVelocity.y / 20.0,
                physicalVelocity.z / 20.0
        );
        setYRot(continuousYaw);
        setXRot(pitch);
        setYBodyRot(continuousYaw);
        setOnGround(snapshot.grounded());
        if (snapshot.grounded()) {
            fallDistance = 0.0F;
        }
        entityData.set(ACTUAL_STEERING, (float) -snapshot.steeringAngle());
        updateSuspensionCompression(snapshot);
        latestPhysicalWheelContactPoints = snapshot.wheelContactPoints();
        latestPhysicalWheelContacts = snapshot.wheelContacts();
        latestPhysicalWheelContactGameTime = level().getGameTime();
        entityData.set(BODY_ROTATION, rotation);
        hasImpulse = true;
    }

    private void synchronizeFallbackBodyRotation() {
        entityData.set(
                BODY_ROTATION,
                new Quaternionf().rotateY((float) -Math.toRadians(getYRot()))
        );
    }

    private void synchronizePoseRotation() {
        entityData.set(PRECISE_YAW, getYRot());
        synchronizeFallbackBodyRotation();
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private void tickGroundMovement(VehiclePerformance performance) {
        Vec3 vehicleDirection = horizontalLookDirection();
        double forwardX = vehicleDirection.x;
        double forwardZ = vehicleDirection.z;
        Vec3 currentMovement = getDeltaMovement();
        double signedSpeed = currentMovement.x * forwardX + currentMovement.z * forwardZ;

        double brakingStep = performance.brakingMetersPerSecondSquared() / 400.0;
        boolean opposingInputs = inputForward && inputBackward;
        boolean unattended = !(getControllingPassenger() instanceof ServerPlayer);
        int requestedThrottle = !performance.canDrive() || unattended || inputBrake || opposingInputs ? 0
                : Integer.compare((inputForward ? 1 : 0) - (inputBackward ? 1 : 0), 0);
        var pedal = fallbackThrottleResponse.update(requestedThrottle, performance.accelerationRampSeconds(), 0.05);
        double accelerationStep = performance.accelerationMetersPerSecondSquared()
                * pedal.averageMagnitude() / 400.0;
        if (unattended || inputBrake || opposingInputs) {
            signedSpeed = approachZero(
                    signedSpeed,
                    unattended ? brakingStep * 2.0 : brakingStep
            );
        } else if (performance.canDrive() && inputForward) {
            signedSpeed = Math.min(
                    performance.maxSpeedMetersPerSecond() / 20.0,
                    signedSpeed + accelerationStep
            );
        } else if (performance.canDrive() && inputBackward) {
            if (signedSpeed > 0.0) {
                signedSpeed = approachZero(signedSpeed, brakingStep);
            } else {
                signedSpeed = Math.max(
                        -performance.reverseSpeedMetersPerSecond() / 20.0,
                        signedSpeed - accelerationStep * 0.6
                );
            }
        } else {
            signedSpeed *= onGround() ? 0.975 : 0.995;
            if (Math.abs(signedSpeed) < 0.001) {
                signedSpeed = 0.0;
            }
        }

        var physical = VehicleTypeParameters.resolve(vehicleTypeId()).physics();
        double steering = fallbackSteeringRack.update(
                Integer.compare((inputRight ? 1 : 0) - (inputLeft ? 1 : 0), 0),
                signedSpeed * 20, onGround(), inputSteeringResponse, 0.05);
        double angle = Math.toRadians(physical.maximumSteeringAngleDegrees()) * steering
                * new VehicleSteeringCurve(physical.steeringLimitStartRatio(), physical.minimumSteeringScale())
                .scale(signedSpeed * 20, performance.maxSpeedMetersPerSecond());
        entityData.set(ACTUAL_STEERING, (float) angle);
        if (Math.abs(signedSpeed) > 0.001) {
            var profile = VehiclePhysicsProfile.require(vehicleTypeId());
            double front = profile.wheels().stream().filter(w -> w.steerable())
                    .mapToDouble(w -> w.wheelCenter().z).average().orElse(1);
            double rear = profile.wheels().stream().filter(w -> !w.steerable())
                    .mapToDouble(w -> w.wheelCenter().z).average().orElse(-1);
            float requestedYaw = getYRot() + (float) Math.toDegrees(signedSpeed * Math.tan(angle)
                    / Math.max(0.1, Math.abs(front - rear)));
            setYRot(resolveStructureYaw(requestedYaw));
            setYBodyRot(getYRot());
        }

        vehicleDirection = horizontalLookDirection();
        forwardX = vehicleDirection.x;
        forwardZ = vehicleDirection.z;
        double verticalSpeed = Math.max(-3.92, currentMovement.y - 0.08);

        Vec3 requestedMovement = new Vec3(
                forwardX * signedSpeed,
                verticalSpeed,
                forwardZ * signedSpeed
        );
        move(MoverType.SELF, requestedMovement);
        if (horizontalCollision) {
            signedSpeed = 0.0;
        }
        if (verticalCollision) {
            verticalSpeed = 0.0;
        }
        setDeltaMovement(
                forwardX * signedSpeed,
                verticalSpeed,
                forwardZ * signedSpeed
        );
    }

    private Vec3 horizontalLookDirection() {
        Vec3 lookDirection = getLookAngle();
        double horizontalLengthSquared = lookDirection.x * lookDirection.x
                + lookDirection.z * lookDirection.z;
        if (horizontalLengthSquared < 1.0E-7) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        double inverseLength = 1.0 / Math.sqrt(horizontalLengthSquared);
        return new Vec3(
                lookDirection.x * inverseLength,
                0.0,
                lookDirection.z * inverseLength
        );
    }

    private void tickBoostTimers() {
        int remaining = vehicleData.boostRemainingTicks();
        int cooldown = vehicleData.boostCooldownTicks();
        if (remaining <= 0 && cooldown <= 0) {
            return;
        }
        int nextRemaining = Math.max(0, remaining - 1);
        int nextCooldown = remaining > 0 ? cooldown : Math.max(0, cooldown - 1);
        boolean boostFinished = remaining > 0 && nextRemaining == 0;
        boolean cooldownFinished = cooldown > 0 && nextCooldown == 0;
        boolean synchronize = tickCount % 20 == 0 || boostFinished || cooldownFinished;
        updateVehicleData(data -> data.withBoostState(
                definition(),
                data.energy(),
                nextCooldown,
                nextRemaining
        ), synchronize);
    }

    private void tickPartAndFuelConsumption() {
        VehicleGameplayConfig config = VehicleGameplayConfig.get();
        boolean moving = horizontalSpeedMetersPerSecond() > 0.05;
        boolean engineDrainTick = vehicleData.engineRunning()
                && tickCount % config.engineDurabilityDrainIntervalTicks() == 0;
        boolean wheelDrainTick = moving
                && tickCount % config.wheelDurabilityDrainIntervalTicks() == 0;
        boolean fuelDrainTick = moving
                && vehicleData.engineRunning()
                && tickCount % config.movingFuelDrainIntervalTicks() == 0;
        boolean chargeTick = horizontalSpeedMetersPerSecond() >= 20.0
                && tickCount % 20 == 0
                && powerDefinition(vehicleData, VehiclePartSlot.EXTRA_POWER_UNIT).isDefined();
        if (!engineDrainTick && !wheelDrainTick && !fuelDrainTick && !chargeTick) {
            return;
        }

        updateVehicleData(data -> {
            VehicleInstanceData updated = data;
            if (engineDrainTick) {
                updated = updated.damagePart(
                        definition(),
                        VehiclePartSlot.ENGINE,
                        config.engineDurabilityDrainAmount()
                );
            }
            if (wheelDrainTick) {
                for (VehiclePartSlot wheelSlot : definition().wheelSlots()) {
                    updated = updated.damagePart(
                            definition(),
                            wheelSlot,
                            config.wheelDurabilityDrainAmount()
                    );
                }
            }
            if (fuelDrainTick) {
                int fuelDrain = VehicleFuelService.consumptionAmount(
                        config.movingFuelDrainMl(),
                        config.boostFuelMultiplier(),
                        data.boostRemainingTicks() > 0
                );
                updated = updated.withFuel(
                        definition(),
                        updated.fuel() - fuelDrain
                );
            }
            if (chargeTick) {
                VehiclePowerDefinition extra = powerDefinition(
                        updated,
                        VehiclePartSlot.EXTRA_POWER_UNIT
                ).get();
                double chargePerSecond = 1.0 + powerDefinition(
                        updated,
                        VehiclePartSlot.ENERGY_RECOVERY_UNIT
                ).fold(() -> 0.0, VehiclePowerDefinition::passiveChargePerSecond);
                VehicleEnergyChargeResult charged = VehicleEnergyService.charge(
                        updated.energy(),
                        updated.energyChargeRemainderMilli(),
                        extra.energyCapacity(),
                        chargePerSecond
                );
                updated = updated.withEnergyCharge(
                        definition(),
                        charged.energy(),
                        charged.remainderMilli()
                );
            }
            if (!updated.engineRunning() && updated.boostRemainingTicks() > 0) {
                updated = updated.withBoostState(
                        definition(),
                        updated.energy(),
                        updated.boostCooldownTicks(),
                        0
                );
            }
            return updated;
        });
    }

    private static Maybe<VehiclePowerDefinition> powerDefinition(
            VehicleInstanceData data,
            VehiclePartSlot slot
    ) {
        return data.part(slot)
                .filter(InstalledPart::enabled)
                .filter(part -> !part.isScrapped())
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(power -> power.matches(part)));
    }

    private static double approachZero(double value, double amount) {
        if (value > 0.0) {
            return Math.max(0.0, value - amount);
        }
        if (value < 0.0) {
            return Math.min(0.0, value + amount);
        }
        return 0.0;
    }

    private void applyVehicleData(VehicleInstanceData data, boolean synchronize) {
        VehicleDefinition definition = VehicleDefinitions.find(data.vehicleType())
                .orElse(defaultDefinition);
        vehicleData = data.sanitized(definition);
        entityData.set(VEHICLE_TYPE, vehicleData.vehicleType().toString());
        entityData.set(FUEL, vehicleData.fuel());
        entityData.set(ENGINE_RUNNING, vehicleData.engineRunning());
        entityData.set(ENERGY, vehicleData.energy());
        entityData.set(
                FRONT_LEFT_WHEEL_INSTALLED,
                vehicleData.part(VehiclePartSlot.FRONT_LEFT_WHEEL).isDefined()
        );
        entityData.set(
                FRONT_RIGHT_WHEEL_INSTALLED,
                vehicleData.part(VehiclePartSlot.FRONT_RIGHT_WHEEL).isDefined()
        );
        entityData.set(
                FRONT_CENTER_WHEEL_INSTALLED,
                vehicleData.part(VehiclePartSlot.FRONT_CENTER_WHEEL).isDefined()
        );
        entityData.set(
                REAR_LEFT_WHEEL_INSTALLED,
                vehicleData.part(VehiclePartSlot.REAR_LEFT_WHEEL).isDefined()
        );
        entityData.set(
                REAR_RIGHT_WHEEL_INSTALLED,
                vehicleData.part(VehiclePartSlot.REAR_RIGHT_WHEEL).isDefined()
        );
        entityData.set(FRONT_LEFT_WHEEL_TIGHTENING,
                tighteningOf(VehiclePartSlot.FRONT_LEFT_WHEEL));
        entityData.set(FRONT_RIGHT_WHEEL_TIGHTENING,
                tighteningOf(VehiclePartSlot.FRONT_RIGHT_WHEEL));
        entityData.set(FRONT_CENTER_WHEEL_TIGHTENING,
                tighteningOf(VehiclePartSlot.FRONT_CENTER_WHEEL));
        entityData.set(REAR_LEFT_WHEEL_TIGHTENING,
                tighteningOf(VehiclePartSlot.REAR_LEFT_WHEEL));
        entityData.set(REAR_RIGHT_WHEEL_TIGHTENING,
                tighteningOf(VehiclePartSlot.REAR_RIGHT_WHEEL));

        if (synchronize && !level().isClientSide()) {
            new VehicleDataSyncPacket(getId(), vehicleData).sendToTrackingEntity(this);
        }
    }

    private int tighteningOf(VehiclePartSlot slot) {
        return vehicleData.part(slot).fold(() -> 0, InstalledPart::tightening);
    }

    private record ClientSnapshot(
            Vec3 position,
            float yaw,
            float pitch,
            Quaternionf rotation
    ) {
        ClientSnapshot {
            Objects.requireNonNull(position, "position");
            rotation = sanitizedRotation(rotation);
        }

        private ClientSnapshot withYaw(float preciseYaw) {
            return new ClientSnapshot(position, preciseYaw, pitch, rotation);
        }

        private ClientSnapshot withRotation(Quaternionf preciseRotation) {
            return new ClientSnapshot(position, yaw, pitch, preciseRotation);
        }
    }
}
