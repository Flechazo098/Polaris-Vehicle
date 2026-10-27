package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import cc.sighs.oelib.event.neoforge.DataReloadEvent;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.registry.ModBlocks;
import com.flechazo.polaris_vehicle.sparkcore.api.SparkLevel;
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsLevelInitEvent;
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsLevelTickEvent;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges logical-server entity ticks and Spark Core's dedicated physics thread.
 */
public final class VehiclePhysicsManager {
    private static final float RAMP_COLLISION_MARGIN = 0.005F;
    private static final Map<PhysicsLevel, ConcurrentHashMap<UUID, Handle>> CONTROLLERS =
            new ConcurrentHashMap<>();
    private static final Map<PhysicsLevel, Set<UUID>> FAILED_CONTROLLERS =
            new ConcurrentHashMap<>();
    private static boolean registered;

    /**
     * Level's task office runs on the server thread; only PhysicsLevel owns native mutations.
     */
    private static void submitPhysicsPre(PhysicsLevel physicsLevel, Runnable task) {
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            task.run();
            return Unit.INSTANCE;
        });
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onPhysicsLevelInit);
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onPhysicsPre);
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onPhysicsPost);
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onLevelTickPre);
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(VehiclePhysicsManager::onVehicleParametersReload);
    }

    private static void onPhysicsLevelInit(PhysicsLevelInitEvent event) {
        PhysicsLevel physicsLevel = event.getLevel();
        registerRampShapes(physicsLevel);
    }

    private static void registerRampShapes(PhysicsLevel physicsLevel) {
        for (RampBlock ramp : ModBlocks.allRamps()) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState state = ramp.defaultBlockState().setValue(RampBlock.FACING, direction);
                HullCollisionShape shape = new HullCollisionShape(
                        RampCollisionGeometry.vertices(direction, ramp.lowHeight(), ramp.rise()));
                shape.setMargin(RAMP_COLLISION_MARGIN);
                physicsLevel.getBlockShapeManager().getSHAPE_CACHE().put(state, shape);
            }
        }
    }

    /**
     * Publish this tick's controls and apply the newest completed Bullet state.
     * Returns false only while the server structure model is unavailable.
     */
    public static boolean tickVehicle(
            AbstractVehicleEntity vehicle,
            VehiclePhysicsInput input
    ) {
        if (vehicle.level().isClientSide() || !SparkLevel.isSparkLevel(vehicle.level())) {
            return false;
        }
        return VehicleStructureModelManager.SERVER.find(vehicle.vehicleTypeId()).fold(
                () -> false,
                model -> tickWithModel(vehicle, input, model)
        );
    }

    private static boolean tickWithModel(
            AbstractVehicleEntity vehicle,
            VehiclePhysicsInput input,
            VehicleCollisionModel model
    ) {
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(vehicle.level());
        if (FAILED_CONTROLLERS.getOrDefault(physicsLevel, Set.of()).contains(vehicle.getUUID())) {
            return false;
        }
        ConcurrentHashMap<UUID, Handle> levelControllers = CONTROLLERS.computeIfAbsent(
                physicsLevel,
                ignored -> new ConcurrentHashMap<>()
        );
        Handle handle = levelControllers.computeIfAbsent(
                vehicle.getUUID(),
                ignored -> createHandle(vehicle, input, model, physicsLevel)
        );
        var addedTo = handle.world();
        if (addedTo != null && addedTo != physicsLevel.getWorld()) {
            // The physics thread restarted after a failure and built a new
            // world; this controller's bodies went away with the old one.
            // Rebuild it from the entity, which kept the last applied pose.
            handle.controller().markRemoved();
            levelControllers.remove(vehicle.getUUID(), handle);
            handle = levelControllers.computeIfAbsent(
                    vehicle.getUUID(),
                    ignored -> createHandle(vehicle, input, model, physicsLevel)
            );
        }
        handle.setVehicle(vehicle);
        handle.controller().publishInput(input);
        // Spark builds terrain collision sections asynchronously. A chassis
        // admitted before its supporting sections are in Bullet falls through
        // the ground during world startup and carries its passenger with it.
        if (!handle.addQueued() && terrainReady(vehicle, handle)) {
            handle.setAddQueued();
            BulletVehicleController controller = handle.controller();
            Handle added = handle;
            submitPhysicsPre(physicsLevel, () -> {
                if (controller.removed()) {
                    return;
                }
                controller.addToWorld(physicsLevel.getWorld());
                added.setWorld(physicsLevel.getWorld());
            });
        }
        // Normally already applied by onLevelTickPre; this covers a batch that
        // was still running when the tick began.
        applyFreshSnapshot(vehicle, handle);
        return true;
    }

    private static void applyFreshSnapshot(AbstractVehicleEntity vehicle, Handle handle) {
        VehiclePhysicsSnapshot latest = handle.controller().latestSnapshot();
        if (latest != null && latest != handle.lastAppliedSnapshot()) {
            vehicle.applyPhysicsSnapshot(latest);
            handle.setLastAppliedSnapshot(latest);
        }
    }

    /**
     * ServerLevel#tick sends tracked-entity changes (ChunkMap#tick) BEFORE it
     * ticks entities. The batch requested at the end of the previous tick has
     * normally finished by now; applying it here lets this tick's movement
     * packets carry it, instead of waiting one more tick in the entity update.
     */
    private static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide() || !SparkLevel.isSparkLevel(event.getLevel())) {
            return;
        }
        ConcurrentHashMap<UUID, Handle> levelControllers =
                CONTROLLERS.get(SparkLevel.getPhysicsLevel(event.getLevel()));
        if (levelControllers == null) {
            return;
        }
        levelControllers.values().forEach(handle -> {
            AbstractVehicleEntity vehicle = handle.vehicle();
            if (vehicle != null && vehicle.level() == event.getLevel() && vehicle.acceptsPhysicsSnapshots()) {
                applyFreshSnapshot(vehicle, handle);
            }
        });
    }

    private static Handle createHandle(
            AbstractVehicleEntity vehicle,
            VehiclePhysicsInput input,
            VehicleCollisionModel model,
            PhysicsLevel physicsLevel
    ) {
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicle.vehicleTypeId());
        BulletVehicleController controller = new BulletVehicleController(
                vehicle.getId(),
                vehicle.getUUID(),
                profile,
                VehicleBulletShapeFactory.getOrCreate(model, profile),
                vehicle.maxUpStep(),
                vehicle.position(),
                vehicle.getYRot(),
                vehicle.getDeltaMovement(),
                input
        );
        return new Handle(controller, findTerrainSupports(vehicle, model));
    }

    private static Set<BlockPos> findTerrainSupports(
            AbstractVehicleEntity vehicle,
            VehicleCollisionModel model
    ) {
        AABB bounds = model.physicsWorldBounds(vehicle.position(), vehicle.getYRot());
        double centerX = (bounds.minX + bounds.maxX) * 0.5;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
        double insetX = Math.min(0.25, bounds.getXsize() * 0.2);
        double insetZ = Math.min(0.25, bounds.getZsize() * 0.2);
        double[] xs = {centerX, bounds.minX + insetX, bounds.maxX - insetX,
                bounds.minX + insetX, bounds.maxX - insetX};
        double[] zs = {centerZ, bounds.minZ + insetZ, bounds.minZ + insetZ,
                bounds.maxZ - insetZ, bounds.maxZ - insetZ};
        Set<BlockPos> supports = new HashSet<>();
        for (int i = 0; i < xs.length; i++) {
            for (int y = (int) Math.floor(bounds.minY - 0.05);
                 y >= Math.floor(bounds.minY - 4.0); y--) {
                BlockPos pos = BlockPos.containing(xs[i], y, zs[i]);
                if (!vehicle.level().getBlockState(pos)
                        .getCollisionShape(vehicle.level(), pos).isEmpty()) {
                    supports.add(pos);
                    break;
                }
            }
        }
        return supports;
    }

    private static boolean terrainReady(AbstractVehicleEntity vehicle, Handle handle) {
        if (handle.supports().isEmpty()) {
            return true;
        }
        Level level = vehicle.level();
        if (level.getGameTime() >= handle.nextTerrainRequestTick()) {
            for (BlockPos support : handle.supports()) {
                SparkLevel.scheduleChunkLoad(level, new ChunkPos(support),
                        support.getY(), support.getY(), 0, 100);
            }
            handle.setNextTerrainRequestTick(level.getGameTime() + 40);
        }
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(level);
        for (BlockPos support : handle.supports()) {
            PhysicsChunkSection section = physicsLevel.getTerrainManager()
                    .getSectionForBlockPos(support);
            if (section == null || !section.isBuilt() || !section.isActive()
                    || section.getPhysicsBody() == null
                    || !section.getPhysicsBody().isInWorld()) {
                return false;
            }
        }
        return true;
    }

    public static void removeVehicle(AbstractVehicleEntity vehicle) {
        if (vehicle.level().isClientSide() || !SparkLevel.isSparkLevel(vehicle.level())) {
            return;
        }
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevel(vehicle.level());
        Set<UUID> failedControllers = FAILED_CONTROLLERS.get(physicsLevel);
        if (failedControllers != null) {
            failedControllers.remove(vehicle.getUUID());
            if (failedControllers.isEmpty()) {
                FAILED_CONTROLLERS.remove(physicsLevel, failedControllers);
            }
        }
        ConcurrentHashMap<UUID, Handle> levelControllers = CONTROLLERS.get(physicsLevel);
        if (levelControllers == null) {
            return;
        }
        Handle removedHandle = levelControllers.remove(vehicle.getUUID());
        if (removedHandle == null) {
            return;
        }
        BulletVehicleController controller = removedHandle.controller();
        controller.markRemoved();
        submitPhysicsPre(physicsLevel, () -> {
            controller.removeFromWorld(physicsLevel.getWorld());
        });
        if (levelControllers.isEmpty()) {
            CONTROLLERS.remove(physicsLevel, levelControllers);
        }
    }

    public static boolean isSleeping(AbstractVehicleEntity vehicle) {
        if (vehicle.level().isClientSide() || !SparkLevel.isSparkLevel(vehicle.level())) return false;
        var handles = CONTROLLERS.get(SparkLevel.getPhysicsLevel(vehicle.level()));
        var handle = handles == null ? null : handles.get(vehicle.getUUID());
        return handle != null && handle.controller().sleeping();
    }

    /**
     * Includes support removals; asleep bodies must not remain suspended over changed terrain.
     */
    public static void terrainChanged(Level level, BlockPos position) {
        var center = Vec3.atCenterOf(position);
        // onBlockStateChange also runs during world initialization, before the
        // physical Level is attached. Only already-created controllers matter.
        for (var entry : CONTROLLERS.entrySet()) {
            if (entry.getKey().getMcLevel() != level) continue;
            for (var handle : entry.getValue().values()) {
                var snapshot = handle.controller().latestSnapshot();
                if (snapshot != null && snapshot.modelOrigin().distanceToSqr(center) <= 16 * 16) {
                    handle.controller().requestWake();
                }
            }
        }
    }

    private static void onPhysicsPre(PhysicsLevelTickEvent.Pre event) {
        ConcurrentHashMap<UUID, Handle> levelControllers = CONTROLLERS.get(event.getLevel());
        if (levelControllers == null) {
            return;
        }
        double timeStep = event.getLevel().getCurrentStepSeconds();
        levelControllers.forEach((id, handle) -> {
            try {
                handle.controller().prePhysicsStep(event.getLevel().getWorld(), timeStep);
            } catch (RuntimeException exception) {
                removeFailedController(event.getLevel(), levelControllers, id, handle, exception);
            }
        });
    }

    private static void onPhysicsPost(PhysicsLevelTickEvent.Post event) {
        // A main-thread tick consumes exactly one completed 50ms batch.
        // Publishing intermediate solver steps lets thread scheduling turn a
        // steady drive into alternating short and long network movements.
        boolean publishSnapshot = event.getLevel().getLastSubstepOfTick();
        ConcurrentHashMap<UUID, Handle> levelControllers = CONTROLLERS.get(event.getLevel());
        if (levelControllers == null) {
            return;
        }
        levelControllers.forEach((id, handle) -> {
            try {
                handle.controller().postPhysicsStep(publishSnapshot);
            } catch (RuntimeException exception) {
                removeFailedController(event.getLevel(), levelControllers, id, handle, exception);
            }
        });
    }

    private static void removeFailedController(
            PhysicsLevel physicsLevel,
            ConcurrentHashMap<UUID, Handle> levelControllers,
            UUID id,
            Handle handle,
            RuntimeException exception
    ) {
        if (!levelControllers.remove(id, handle)) {
            return;
        }
        handle.controller().markRemoved();
        FAILED_CONTROLLERS.computeIfAbsent(
                physicsLevel,
                ignored -> ConcurrentHashMap.newKeySet()
        ).add(id);
        handle.controller().removeFromWorld(physicsLevel.getWorld());
        PolarisVehicle.LOGGER.error(
                "[vehicle-physics] disabled failed Bullet controller entityUuid={}",
                id,
                exception
        );
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        FAILED_CONTROLLERS.entrySet().removeIf(entry -> entry.getKey().getMcLevel() == event.getLevel());
        CONTROLLERS.entrySet().removeIf(entry -> {
            if (entry.getKey().getMcLevel() != event.getLevel()) {
                return false;
            }
            entry.getValue().values().forEach(handle -> {
                handle.controller().markRemoved();
                submitPhysicsPre(entry.getKey(),
                        () -> handle.controller().removeFromWorld(entry.getKey().getWorld()));
            });
            entry.getValue().clear();
            return true;
        });
    }

    /**
     * Recreates controllers so changes made by the visual data-pack editor apply immediately.
     */
    private static void onVehicleParametersReload(DataReloadEvent event) {
        if (!event.isDataType(VehicleTypeParameters.class)) {
            return;
        }
        VehicleBulletShapeFactory.clearCache();
        FAILED_CONTROLLERS.clear();
        CONTROLLERS.forEach((physicsLevel, controllers) -> {
            controllers.values().forEach(handle -> {
                BulletVehicleController controller = handle.controller();
                controller.markRemoved();
                submitPhysicsPre(physicsLevel, () -> {
                    controller.removeFromWorld(physicsLevel.getWorld());
                });
            });
            controllers.clear();
        });
        CONTROLLERS.clear();
    }

    private static final class Handle {
        private final BulletVehicleController controller;
        private final Set<BlockPos> supports;
        private volatile VehiclePhysicsSnapshot lastAppliedSnapshot;
        /**
         * The server entity that last published input; main thread only.
         */
        private AbstractVehicleEntity vehicle;
        private boolean addQueued;
        private long nextTerrainRequestTick;
        /**
         * The physics world this controller was added to; set on the physics thread.
         */
        private volatile PhysicsSpace world;

        Handle(BulletVehicleController controller, Set<BlockPos> supports) {
            this.controller = controller;
            this.supports = supports;
        }

        private BulletVehicleController controller() {
            return controller;
        }

        private VehiclePhysicsSnapshot lastAppliedSnapshot() {
            return lastAppliedSnapshot;
        }

        private void setLastAppliedSnapshot(VehiclePhysicsSnapshot snapshot) {
            lastAppliedSnapshot = snapshot;
        }

        private AbstractVehicleEntity vehicle() {
            return vehicle;
        }

        private void setVehicle(AbstractVehicleEntity vehicle) {
            this.vehicle = vehicle;
        }

        private Set<BlockPos> supports() {
            return supports;
        }

        private boolean addQueued() {
            return addQueued;
        }

        private void setAddQueued() {
            addQueued = true;
        }

        private long nextTerrainRequestTick() {
            return nextTerrainRequestTick;
        }

        private void setNextTerrainRequestTick(long tick) {
            nextTerrainRequestTick = tick;
        }

        private PhysicsSpace world() {
            return world;
        }

        private void setWorld(PhysicsSpace world) {
            this.world = world;
        }
    }
}
