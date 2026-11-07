// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.level;

import com.flechazo.hkt.business.concurrent.ExecutorResources;
import com.flechazo.hkt.business.concurrent.ExecutorShutdownPolicy;
import com.flechazo.hkt.business.concurrent.ThreadConfig;
import com.flechazo.hkt.business.effect.ExitCase;
import com.flechazo.hkt.business.effect.Resource;
import com.flechazo.hkt.business.util.ThrowableSupport;
import com.flechazo.polaris_vehicle.sparkcore.SparkCore;
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsLevelTickEvent;
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.PhysicsBodyExtension;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.BlockShapeManager;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkManager;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection;
import com.flechazo.polaris_vehicle.sparkcore.util.DelayedTask;
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase;
import com.flechazo.polaris_vehicle.sparkcore.util.SparkMath;
import com.flechazo.polaris_vehicle.sparkcore.util.TaskCompletion;
import com.flechazo.polaris_vehicle.sparkcore.util.TaskSubmitOffice;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsMembership;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsStep;
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsStepGate;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class PhysicsLevel implements AutoCloseable, TaskSubmitOffice, PhysicsTickListener {
    public static final long TICK_BUDGET_NS = 45_000_000L, TICK_TARGET_NS = 45_000_000L;
    public static final double OVERLOAD_THRESHOLD_RATIO = 0.9, RECOVER_THRESHOLD_RATIO = 0.5;
    private final String name;
    private final Level mcLevel;
    private final int baseStep, tps;
    private final boolean singleThreadMode;
    private volatile int dynamicRepeat, minStep = 3, maxStep;
    private final AtomicInteger tickCount = new AtomicInteger();
    private volatile boolean initialized;
    private volatile boolean closed;
    private volatile PhysicsLevelState state = PhysicsLevelState.IDLE;
    private float currentStepSeconds;
    private boolean lastSubstepOfTick = true;
    private double smoothedStepTime = TICK_BUDGET_NS;
    private int adjustmentCooldown, overloadWarnCooldown;
    private long targetStepTime;
    private volatile long lastStepTickTime, lastPhysicsTickTime = System.nanoTime();
    private Throwable deferredFailure;
    private final VehiclePhysicsStepGate stepGate = new VehiclePhysicsStepGate();
    private final AtomicInteger crashCount = new AtomicInteger();
    private final Object lifecycleLock = new Object();
    private Resource.Allocation<ExecutorService> worker;
    private volatile Throwable terminalFailure;
    private volatile Throwable closeFailure;
    private volatile boolean nativeResourcesDestroyed;
    private volatile Thread workerThread;
    private Runnable initCallback;
    private PhysicsWorld world;
    private PhysicsChunkManager terrainManager;
    private BlockShapeManager blockShapeManager;
    private final ConcurrentHashMap<PhysicsHost, Map<String, PhysicsCollisionObject>> hostManager =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Runnable>> taskMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Runnable>> immediateQueue =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>> delayedTaskMap =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>> delayedTaskQueue =
            new ConcurrentHashMap<>();

    protected PhysicsLevel(String name, Level level, int baseStep, boolean singleThreadMode) {
        this.name = name;
        mcLevel = level;
        this.baseStep = baseStep;
        this.singleThreadMode = singleThreadMode;
        tps = baseStep * 20;
        dynamicRepeat = baseStep;
        maxStep = baseStep;
        currentStepSeconds = 1F / tps;
    }

    public String getName() {
        return name;
    }

    public Level getMcLevel() {
        return mcLevel;
    }

    public int getBaseStep() {
        return baseStep;
    }

    public int getTps() {
        return tps;
    }

    public float getCurrentStepSeconds() {
        return currentStepSeconds;
    }

    public boolean getLastSubstepOfTick() {
        return lastSubstepOfTick;
    }

    public int getDynamicRepeat() {
        return dynamicRepeat;
    }

    public synchronized void setDynamicRepeat(int value) {
        dynamicRepeat = value;
    }

    public int getMinStep() {
        return minStep;
    }

    public synchronized void setMinStep(int value) {
        minStep = Math.max(1, value);
        dynamicRepeat = Math.max(dynamicRepeat, minStep);
    }

    public int getMaxStep() {
        return maxStep;
    }

    public synchronized void setMaxStep(int value) {
        maxStep = Math.max(minStep, value);
        dynamicRepeat = Math.min(dynamicRepeat, maxStep);
    }

    public int getDefaultMinStep() {
        return 1;
    }

    public int getDefaultMaxStep() {
        return baseStep;
    }

    public long getTargetStepTime() {
        return targetStepTime;
    }

    public void setTargetStepTime(long value) {
        targetStepTime = value;
    }

    public long getLastStepTickTime() {
        return lastStepTickTime;
    }

    public long getLastPhysicsTickTime() {
        return lastPhysicsTickTime;
    }

    public PhysicsLevelState getState() {
        return state;
    }

    public PhysicsWorld getWorld() {
        return world;
    }

    public PhysicsChunkManager getTerrainManager() {
        return terrainManager;
    }

    public BlockShapeManager getBlockShapeManager() {
        return blockShapeManager;
    }

    public ConcurrentHashMap<PhysicsHost, Map<String, PhysicsCollisionObject>> getHostManager() {
        return hostManager;
    }

    @Override
    public int getTickCount() {
        return tickCount.get();
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Runnable>> getTaskMap() {
        return taskMap;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Runnable>> getImmediateQueue() {
        return immediateQueue;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>> getDelayedTaskMap() {
        return delayedTaskMap;
    }

    @Override
    public ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>> getDelayedTaskQueue() {
        return delayedTaskQueue;
    }

    public synchronized void deferFailure(Throwable failure) {
        if (deferredFailure == null) deferredFailure = failure;
        else if (deferredFailure != failure) deferredFailure.addSuppressed(failure);
    }

    public synchronized void rethrowDeferredFailure() {
        var failure = deferredFailure;
        if (failure == null) return;
        deferredFailure = null;
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        throw new IllegalStateException(failure);
    }

    private void stepPhysics() throws InterruptedException {
        int steps = dynamicRepeat;
        float dt = VehiclePhysicsStep.seconds(steps);
        currentStepSeconds = dt;
        long started = System.nanoTime(), slept = 0;
        state = PhysicsLevelState.RUNNING;
        long sleep = VehiclePhysicsStep.sleepMillis(targetStepTime, smoothedStepTime, steps);
        for (int i = 0; i < steps; i++) {
            tickCount.incrementAndGet();
            lastSubstepOfTick = i == steps - 1;
            VehiclePhysicsMembership.repairBeforeStep(world);
            world.update(dt, 0, false, true, false, true);
            rethrowDeferredFailure();
            if (sleep > 0 && i < steps - 1) {
                long before = System.nanoTime();
                Thread.sleep(sleep);
                slept += System.nanoTime() - before;
            }
            lastPhysicsTickTime = System.nanoTime();
        }
        double active = VehiclePhysicsStep.activeNanos(System.nanoTime() - started, slept);
        double alpha = active > smoothedStepTime ? 0.9 : 0.5;
        smoothedStepTime = alpha * active + (1 - alpha) * smoothedStepTime;
        adjustSubsteps();
        lastStepTickTime = System.nanoTime() - started;
        state = PhysicsLevelState.IDLE;
    }

    private synchronized void adjustSubsteps() {
        if (adjustmentCooldown > 0) adjustmentCooldown--;
        else if (smoothedStepTime > TICK_BUDGET_NS * OVERLOAD_THRESHOLD_RATIO
                && dynamicRepeat > minStep) {
            dynamicRepeat--;
            adjustmentCooldown = 20;
            SparkCore.LOGGER.warn(
                    "{} physics overload detected, step reduced to {}", name, dynamicRepeat);
        } else if (smoothedStepTime < TICK_BUDGET_NS * RECOVER_THRESHOLD_RATIO
                && dynamicRepeat < maxStep) {
            dynamicRepeat++;
            adjustmentCooldown = 20;
            SparkCore.LOGGER.warn(
                    "{} physics recovered, step increased to {}", name, dynamicRepeat);
        }
    }

    private Throwable executeStep() {
        if (!stepGate.beginStep()) return null;
        Throwable failure = null;
        try {
            stepPhysics();
        } catch (Throwable error) {
            failure = error;
            terminalFailure = error;
        } finally {
            stepGate.finishStep();
        }
        return failure;
    }

    private void runStep() {
        var failure = executeStep();
        if (failure != null) handleException(failure);
    }

    public void requestStep() {
        if (!stepGate.tryPrepare()) return;
        Throwable failure = null;
        try {
            if (!initialized || state == PhysicsLevelState.RUNNING) return;
            if (overloadWarnCooldown > 0) overloadWarnCooldown--;
            if (lastStepTickTime / 1000000 >= 50 && overloadWarnCooldown <= 0) {
                SparkCore.LOGGER.warn(
                        "{} tick {} overloaded, last tick time: {}ms, speed: {}%, rigid body in"
                                + " world: {} with {}, while {} chunks loaded.",
                        name,
                        tickCount.get(),
                        lastStepTickTime / 1000000,
                        dynamicRepeat * 100 / baseStep,
                        world.getPcoList().size(),
                        terrainManager.getStats(),
                        mcLevel.getChunkSource().getLoadedChunksCount());
                overloadWarnCooldown = 100;
            }
            var build = new ArrayList<AABB>();
            var activation = new ArrayList<AABB>();
            for (var body : world.getPcoList()) {
                var snapshot = PhysicsBodyExtension.stateOf(body);
                snapshot.update();
                var owner = PhysicsBodyExtension.getOwner(body);
                if (!body.isStatic()
                        && !(owner instanceof PhysicsChunkSection)
                        && ((body.getCollideWithGroups() & CollisionGroups.TERRAIN) != 0
                                || owner instanceof Player)) {
                    var box = SparkMath.toAABB(snapshot.getCachedBoundingBox());
                    if (body instanceof PhysicsRigidBody rigid) {
                        var delta =
                                SparkMath.toVec3(rigid.getLinearVelocity(null)).scale(1.5 / tps);
                        if (delta.length() < 5) box = box.expandTowards(delta);
                    }
                    if (owner instanceof Player) box = box.expandTowards(0, -1.1, 0);
                    build.add(box);
                    activation.add(box);
                }
            }
            terrainManager.processPendingBuildRequests();
            terrainManager.updateWeatherSlipIfNeeded();
            terrainManager.updateDirtySections();
            terrainManager.updateBuild(build);
            terrainManager.updateActivation(activation);
            terrainManager.updateScheduledChunks();
            stepGate.reserve();
            if (singleThreadMode) failure = executeStep();
            else
                try {
                    worker.value().execute(this::runStep);
                } catch (RejectedExecutionException error) {
                    stepGate.cancelReservation();
                    throw error;
                }
        } finally {
            stepGate.unlock();
        }
        if (failure != null) handleException(failure);
    }

    public void start() {
        start(null);
    }

    public void start(Runnable onInitialized) {
        if (Thread.currentThread() == workerThread || stepGate.isHeldByCurrentThread())
            throw new IllegalStateException(
                    "Physics lifecycle changes must run outside the physics worker");
        synchronized (lifecycleLock) {
            if (worker != null || initialized || world != null || terrainManager != null)
                throw new IllegalStateException("Physics level is already started");
            if (onInitialized != null) initCallback = onInitialized;
            state = PhysicsLevelState.IDLE;
            closed = false;
            terminalFailure = null;
            closeFailure = null;
            nativeResourcesDestroyed = false;
            stepGate.reopen();
            PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING);
            New6Dof.logger2.setLevel(java.util.logging.Level.WARNING);
            SparkCore.LOGGER.info(
                    "启动物理线程：{}，线程数：{}/{}, threadSafe:{}, Debug:{}, singleThread:{}",
                    name,
                    Runtime.getRuntime().availableProcessors(),
                    NativeLibrary.countThreads(),
                    NativeLibrary.isThreadSafe(),
                    NativeLibrary.isDebug(),
                    singleThreadMode);
            try {
                if (singleThreadMode) initialize();
                else {
                    worker =
                            ExecutorResources.fixed(
                                            1,
                                            ThreadConfig.platform(name),
                                            ExecutorShutdownPolicy.defaults())
                                    .allocate()
                                    .unsafeRun();
                    TaskCompletion.await(
                            worker.value()
                                    .submit(
                                            () -> {
                                                workerThread = Thread.currentThread();
                                                initialize();
                                            }));
                }
            } catch (Throwable failure) {
                terminalFailure = failure;
                try {
                    close(exitCase(failure));
                } catch (Throwable cleanup) {
                    ThrowableSupport.suppress(failure, cleanup);
                }
                throw ThrowableSupport.propagate(failure);
            }
        }
    }

    private void initialize() {
        if (!stepGate.beginInitialization()) return;
        try {
            world = new PhysicsWorld(this);
            terrainManager = new PhysicsChunkManager(this);
            blockShapeManager = new BlockShapeManager(this);
            if (initCallback != null) initCallback.run();
            initialized = true;
        } finally {
            stepGate.unlock();
        }
    }

    @Override
    public void close() {
        var failure = terminalFailure;
        close(
                failure != null
                        ? exitCase(failure)
                        : Thread.currentThread().isInterrupted()
                                ? ExitCase.canceled(
                                        new InterruptedException("Physics level close interrupted"))
                                : ExitCase.succeeded());
    }

    private void close(ExitCase exit) {
        if (Thread.currentThread() == workerThread || stepGate.isHeldByCurrentThread())
            throw new IllegalStateException(
                    "Physics worker cannot synchronously close its own executor");
        synchronized (lifecycleLock) {
            if (closed) {
                if (closeFailure != null) throw ThrowableSupport.propagate(closeFailure);
                return;
            }
            stepGate.beginClose();
            Resource.Allocation<ExecutorService> allocation;
            ExitCase scopeExit;
            try {
                scopeExit = exit instanceof ExitCase.Succeeded && terminalFailure != null
                        ? exitCase(terminalFailure) : exit;
                initialized = false;
                closed = true;
                allocation = worker;
                worker = null;
            } finally {
                stepGate.unlock();
            }
            Throwable failure = null;
            try {
                if (allocation == null) destroyResources(scopeExit);
                else TaskCompletion.await(allocation.value().submit(() -> destroyResources(scopeExit)));
            } catch (Throwable error) {
                failure = error;
            }
            try {
                if (allocation != null)
                    allocation
                            .release(
                                    scopeExit instanceof ExitCase.Succeeded && failure != null
                                            ? exitCase(failure)
                                            : scopeExit)
                            .execute();
            } catch (Throwable error) {
                failure = failure == null ? error : ThrowableSupport.suppress(failure, error);
            } finally {
                workerThread = null;
                if (nativeResourcesDestroyed
                        && (allocation == null || allocation.value().isTerminated())
                        && (terrainManager == null || terrainManager.isBuilderTerminated())) {
                    terrainManager = null;
                    blockShapeManager = null;
                    world = null;
                }
                hostManager.clear();
                taskMap.clear();
                immediateQueue.clear();
                delayedTaskMap.clear();
                delayedTaskQueue.clear();
            }
            if (failure != null) {
                Throwable cause =
                        scopeExit instanceof ExitCase.Failed failed
                                ? failed.cause()
                                : scopeExit instanceof ExitCase.Canceled canceled
                                        ? canceled.cause()
                                        : null;
                if (cause != null) failure = ThrowableSupport.suppress(cause, failure);
                closeFailure = failure;
                throw ThrowableSupport.propagate(failure);
            }
        }
    }

    private static ExitCase exitCase(Throwable failure) {
        Throwable cause = ThrowableSupport.unwrapAsync(failure);
        return ThrowableSupport.isCancellation(cause)
                ? ExitCase.canceled(cause)
                : ExitCase.failed(cause);
    }

    private void destroyResources(ExitCase exit) {
        Throwable failure = null;
        try {
            if (terrainManager != null) terrainManager.destroy(exit);
        } catch (Throwable error) {
            failure = error;
        }
        if (terrainManager != null && !terrainManager.isBuilderTerminated()) {
            if (failure != null) throw ThrowableSupport.propagate(failure);
            throw new IllegalStateException(
                    "Terrain builder is still running; native resources retained");
        }
        try {
            if (blockShapeManager != null) blockShapeManager.getSHAPE_CACHE().clear();
            if (world != null) world.destroy();
            nativeResourcesDestroyed = true;
        } catch (Throwable error) {
            failure = failure == null ? error : ThrowableSupport.suppress(failure, error);
        }
        if (failure != null) throw ThrowableSupport.propagate(failure);
    }

    public void restart() {
        if (singleThreadMode) {
            SparkCore.LOGGER.warn("{} 单线程模式下不支持自动重启，跳过 restart()", name);
            return;
        }
        close();
        start(initCallback);
    }

    private void handleException(Throwable failure) {
        initialized = false;
        terminalFailure = failure;
        SparkCore.LOGGER.error("物理线程崩溃！尝试重启...", failure);
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        boolean recover = !singleThreadMode && crashCount.incrementAndGet() < 3;
        Runnable cleanup =
                () -> {
                    if (closed) return;
                    if (recover) restart();
                    else {
                        SparkCore.LOGGER.error("物理线程连续崩溃，停止恢复！");
                        close(exitCase(failure));
                    }
                };
        var server = mcLevel.getServer();
        if (server != null) server.execute(cleanup);
        else
            Thread.ofPlatform()
                    .daemon(true)
                    .name(name + "-cleanup")
                    .start(
                            () -> {
                                if (!closed) close(exitCase(failure));
                            });
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float dt) {
        for (var body : world.getPcoList()) body.isColliding = false;
        NeoForge.EVENT_BUS.post(new PhysicsLevelTickEvent.Pre(this));
        processTasks(PPhase.ALL);
        processTasks(PPhase.PRE);
    }

    @Override
    public void physicsTick(PhysicsSpace space, float dt) {
        NeoForge.EVENT_BUS.post(new PhysicsLevelTickEvent.Post(this));
        processTasks(PPhase.ALL);
        processTasks(PPhase.POST);
    }

    public abstract List<Entity> requestEntities();
}
