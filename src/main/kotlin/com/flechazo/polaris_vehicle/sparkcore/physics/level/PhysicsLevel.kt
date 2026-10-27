// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/level/PhysicsLevel.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.level

import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsMembership
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsStep
import com.flechazo.polaris_vehicle.vehicle.physics.VehiclePhysicsStepGate

import com.flechazo.polaris_vehicle.sparkcore.SparkCore
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsEntityTickEvent
import com.flechazo.polaris_vehicle.sparkcore.event.PhysicsLevelTickEvent
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost
import com.flechazo.polaris_vehicle.sparkcore.physics.body.*
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.BlockShapeManager
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkManager
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection
import com.flechazo.polaris_vehicle.sparkcore.util.*
import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.PhysicsTickListener
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.joints.New6Dof
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

abstract class PhysicsLevel(
    val name: String,
    open val mcLevel: Level,
    open val baseStep: Int = 5,
    /** 单线程模式：物理计算在主线程同步执行，不创建独立协程。调试/兼容性诊断用。 */
    val singleThreadMode: Boolean = false,
) : AutoCloseable, TaskSubmitOffice, PhysicsTickListener {

    companion object {
        /** 主线程tick预算时间（纳秒），MC为20Hz → 50ms，留出一些线程调度和数据同步余量 */
        const val TICK_BUDGET_NS = 45_000_000L

        /** 目标每tick时间（纳秒），MC为20Hz → 50ms，留出一些线程调度和数据同步余量 */
        const val TICK_TARGET_NS = 45_000_000L

        /** 降频阈值：超过预算的比例 */
        const val OVERLOAD_THRESHOLD_RATIO = 0.9  // 90%预算开始降频

        /** 升频阈值：低于预算的比例 */
        const val RECOVER_THRESHOLD_RATIO = 0.5  // 50%预算以下尝试升频

        /** 快速响应系数：负载上升时靠近新值 */
        private const val ATTACK_ALPHA = 0.9

        /** 缓慢恢复系数：负载下降时回落 */
        private const val DECAY_ALPHA = 0.5
    }

    val tps = baseStep * 20
    var currentStepSeconds = 1f / tps
        private set
    var lastSubstepOfTick = true
        private set

    /** 当前动态步进次数 */
    @Volatile
    var dynamicRepeat = baseStep

    /** 允许的最小每tick步进次数（可运行时调整） */
    @Volatile
    var minStep: Int = 3
        set(value) {
            field = value.coerceAtLeast(1)
            if (dynamicRepeat < field) dynamicRepeat = field
        }

    val defaultMinStep: Int = 1

    /** 允许的最大每tick步进次数（可运行时调整） */
    @Volatile
    var maxStep: Int = baseStep
        set(value) {
            field = value.coerceAtLeast(minStep)
            if (dynamicRepeat > field) dynamicRepeat = field
        }

    val defaultMaxStep: Int = baseStep

    /** 平滑步进时间：过去约n跳的平均值 */
    private var smoothedStepTime = TICK_BUDGET_NS.toDouble()

    /** 调整冷却：修改频率后至少等待多少tick才能再次修改 */
    private var adjustmentCooldown = 0

    /** 目标步进时间：完成一轮物理计算的目标用时, 设为0以尽可能快速完成，更新之间不插入休眠 */
    var targetStepTime = 0L

    // 协程配置。崩溃重启时旧线程池已关闭、作用域已取消，start() 会换一套新的。
    var dispatcher = newDispatcher()
        private set
    var scope = newScope()
        private set
    private var initCallback: (() -> Unit)? = null

    private fun newDispatcher() = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    private fun newScope() =
        CoroutineScope(dispatcher + CoroutineName(name) + SupervisorJob() + CoroutineExceptionHandler(::handleException))

    /**
     * An exception thrown inside a Bullet callback does not stop the native
     * step: Bullet carries on, making further Java calls with it pending.
     * Callbacks record it here; it is rethrown once the native step returns.
     */
    @Volatile
    private var deferredFailure: Throwable? = null

    fun deferFailure(failure: Throwable) {
        val first = deferredFailure
        if (first == null) deferredFailure = failure else if (first !== failure) first.addSuppressed(failure)
    }

    fun rethrowDeferredFailure() {
        val failure = deferredFailure ?: return
        deferredFailure = null
        throw failure
    }

    // 状态管理
    private val stateFlow = MutableStateFlow(PhysicsLevelState.IDLE)
    val state = stateFlow.asStateFlow()
    private val crashCount = AtomicInteger(0)
    @Volatile
    override var tickCount: Int = 0

    // 同步控制
    private val physicsTickChannel = Channel<Unit>(Channel.CONFLATED)
    private val stepCompletedChannel = Channel<Unit>(Channel.CONFLATED)
    private val stepGate = VehiclePhysicsStepGate()
    @Volatile
    private var initialized = false

    // 运行时数据
    lateinit var world: PhysicsWorld
        private set
    val hostManager = ConcurrentHashMap<PhysicsHost, MutableMap<String, PhysicsCollisionObject>>()
    override val taskMap = ConcurrentHashMap<PPhase, ConcurrentHashMap<String, () -> Unit>>()
    override val immediateQueue = ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<() -> Unit>>()
    override val delayedTaskMap = ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>>()
    override val delayedTaskQueue = ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>>()
    @Volatile
    var lastStepTickTime = 0L
    @Volatile
    var lastPhysicsTickTime = System.nanoTime()
    var overloadWarnCooldown = 0

    var entities = listOf<Entity>()
        private set

    //地形碰撞相关
    lateinit var terrainManager: PhysicsChunkManager
    lateinit var blockShapeManager: BlockShapeManager

    suspend fun CoroutineScope.run() {
        while (isActive) {
            physicsTickChannel.receive()
            if (!stepGate.beginStep()) continue
            try {
                stepPhysics()
            } finally {
                stepGate.finishStep()
            }
            stepCompletedChannel.send(Unit)
        }
    }

    /**
     * 同步执行一轮物理步进（dynamicRepeat 次 world.update()）。
     * 单线程模式下由 requestStep() 直接调用，多线程模式下由 run() 循环内调用。
     */
    private fun stepPhysics() {
        val stepsThisTick = dynamicRepeat
        val fixedStep = VehiclePhysicsStep.seconds(stepsThisTick)
        currentStepSeconds = fixedStep
        val ticker = System.nanoTime()
        stateFlow.value = PhysicsLevelState.RUNNING
        val stepSleepMillis = VehiclePhysicsStep.sleepMillis(targetStepTime, smoothedStepTime, stepsThisTick)
        var sleptNanos = 0L
        // 动态步进次数
        repeat(stepsThisTick) { stepIndex ->
            tickCount++
            lastSubstepOfTick = stepIndex == stepsThisTick - 1
            VehiclePhysicsMembership.repairBeforeStep(world)
            world.update(fixedStep, 0, false, true, false, true)
            rethrowDeferredFailure()

            // 计算当前步的休眠时间（除了最后一步）
            if (stepSleepMillis > 0 && stepIndex < stepsThisTick - 1) {
                // 单线程模式用 Thread.sleep 替代协程 delay
                val sleepStart = System.nanoTime()
                Thread.sleep(stepSleepMillis)
                sleptNanos += System.nanoTime() - sleepStart // 转换为毫秒
            }
            // ===== 动态调节逻辑 =====
            lastPhysicsTickTime = System.nanoTime()
        }
        val currentMs = VehiclePhysicsStep.activeNanos(System.nanoTime() - ticker, sleptNanos).toDouble()
        smoothedStepTime = if (currentMs > smoothedStepTime) {
            // 负载上升：快速跟随 (Attack)
            (ATTACK_ALPHA * currentMs) + (1.0 - ATTACK_ALPHA) * smoothedStepTime
        } else {
            // 负载下降：缓慢回落 (Decay)
            (DECAY_ALPHA * currentMs) + (1.0 - DECAY_ALPHA) * smoothedStepTime
        }
        if (adjustmentCooldown > 0) {
            adjustmentCooldown--
        } else {
            val overloadThreshold = (TICK_BUDGET_NS * OVERLOAD_THRESHOLD_RATIO).toLong()
            val recoveryThreshold = (TICK_BUDGET_NS * RECOVER_THRESHOLD_RATIO).toLong()
            when {
                smoothedStepTime > overloadThreshold && dynamicRepeat > minStep -> {
                    dynamicRepeat--
                    adjustmentCooldown = 20 // 降频后观察1秒（20tick）再做决定
                    SparkCore.LOGGER.warn(
                        "{} physics overload detected, step reduced to {}",
                        name,
                        dynamicRepeat
                    )
                }

                smoothedStepTime < recoveryThreshold && dynamicRepeat < maxStep -> {
                    dynamicRepeat++
                    adjustmentCooldown = 20
                    SparkCore.LOGGER.warn(
                        "{} physics recovered, step increased to {}",
                        name,
                        dynamicRepeat
                    )
                }
            }
        }
        // 通知主线程计算完成
        lastStepTickTime = System.nanoTime() - ticker
        stateFlow.value = PhysicsLevelState.IDLE
    }

    /**
     * 在主线程每tick调用，向物理线程发送模拟请求（或多线程模式直接同步调用）。
     * 多线程模式下物理线程接收到请求后会立刻模拟约1主线程tick时间的物理步进，
     * 此时主线程会继续执行后续内容。单线程模式下直接同步执行。
     */
    fun requestStep() {
        if (!stepGate.tryPrepare()) return
        try {
            if (!initialized) return
            // 如果物理线程已经在运行，跳过此次同步等待其完成
            if (stateFlow.value == PhysicsLevelState.RUNNING) {
                return
            }
            // 警告信息
            if (overloadWarnCooldown > 0) overloadWarnCooldown--
            if ((lastStepTickTime / 1000000) >= 50 && overloadWarnCooldown <= 0) {
                SparkCore.LOGGER.warn(
                    "{} tick {} overloaded, last tick time: {}ms, speed: {}%, rigid body in world: {} with {}, while {} chunks loaded.",
                    name,
                    tickCount,
                    (lastStepTickTime / 1000000).toInt(),
                    dynamicRepeat * 100 / baseStep,
                    world.pcoList.size,
                    terrainManager.getStats(),
                    mcLevel.chunkSource.loadedChunksCount
                )
                overloadWarnCooldown = 100
            }
            // 收集所有需要激活地形的刚体的包围盒
            val buildBoxes = mutableListOf<AABB>()
            val activationBoxes = mutableListOf<AABB>()

            // 遍历所有刚体，更新其状态
            world.pcoList.forEach {
                stateOf(it).update()
                // 收集所有需要激活地形的刚体的包围盒
                val owner = it.owner
                if (!it.isStatic && owner !is PhysicsChunkSection) {
                    if (it.collideWithGroups and CollisionGroups.TERRAIN != 0 || owner is Player)
                        run {
                            var aabb = stateOf(it).cachedBoundingBox.toAABB()
                            if (it is PhysicsRigidBody) {
                                val delta = it.getLinearVelocity(null).toVec3().scale(1.5 / tps)
                                if (delta.length() < 5f)
                                    aabb = aabb.expandTowards(delta)
                            }
                            if (owner is Player) aabb = aabb.expandTowards(0.0, -1.1, 0.0)
                            buildBoxes.add(aabb)
                            activationBoxes.add(aabb)
                        }
                }
            }

            // 统一更新地形
            terrainManager.processPendingBuildRequests() // ★ API 预约请求先消费（确保物理线程 IDLE 时构建）
            terrainManager.updateWeatherSlipIfNeeded()
            terrainManager.updateDirtySections()
            terrainManager.updateBuild(buildBoxes)
            terrainManager.updateActivation(activationBoxes)
            // 清理过期的预约调度（投射物区块加载释放）
            terrainManager.updateScheduledChunks()

            stepGate.reserve()
            if (singleThreadMode) {
                if (stepGate.beginStep()) {
                    try { stepPhysics() } finally { stepGate.finishStep() }
                }
            } else {
                val sent = physicsTickChannel.trySend(Unit)
                if (sent.isFailure) {
                    stepGate.cancelReservation()
                    sent.getOrThrow()
                }
            }
    
        } finally { stepGate.unlock() }
    }

    /**
     * 启动物理系统并初始化。
     * 多线程模式下创建独立协程执行物理循环，单线程模式下同步初始化（不创建协程）。
     */
    fun start(onInitialized: (() -> Unit)? = null) {
        // A restart re-runs the same initialization (ramp shapes and the like).
        if (onInitialized != null) initCallback = onInitialized
        // A step that failed never got back to IDLE, and requestStep() skips
        // while RUNNING: the restarted thread would never be asked to step.
        stateFlow.value = PhysicsLevelState.IDLE
        stepGate.reopen()
        PhysicsRigidBody.logger2.setLevel(java.util.logging.Level.WARNING) // 防止创建log刷屏
        New6Dof.logger2.setLevel(java.util.logging.Level.WARNING)
        SparkCore.LOGGER.info(
            "启动物理线程：{}，线程数：{}/{}, threadSafe:{}, Debug:{}, singleThread:{}",
            name,
            Runtime.getRuntime().availableProcessors(),
            NativeLibrary.countThreads(),
            NativeLibrary.isThreadSafe(),
            NativeLibrary.isDebug(),
            singleThreadMode
        )

        if (singleThreadMode) {
            // ★ 单线程模式：同步初始化，不创建协程
            if (!stepGate.beginInitialization()) return
            try {
                world = PhysicsWorld(this@PhysicsLevel)
                terrainManager = PhysicsChunkManager(this@PhysicsLevel)
                blockShapeManager = BlockShapeManager(this@PhysicsLevel)
                onInitialized?.invoke()
                initialized = true
            } finally { stepGate.unlock() }
            // Initialization callback runs under the lifecycle lock below.
        } else {
            // 多线程模式：协程内初始化 + 启动 run() 循环
            if (!scope.isActive) {
                dispatcher = newDispatcher()
                scope = newScope()
            }
            scope.launch {
                if (!stepGate.beginInitialization()) return@launch
                try {
                    world = PhysicsWorld(this@PhysicsLevel)
                    terrainManager = PhysicsChunkManager(this@PhysicsLevel)
                    blockShapeManager = BlockShapeManager(this@PhysicsLevel)
                    onInitialized?.invoke()
                    initialized = true
                } finally { stepGate.unlock() }

                // 初始化完成，执行回调
                // Initialization callback runs under the lifecycle lock below.

                run()
            }
        }
    }

    /**
     * 关闭物理线程并清理资源。
     * 多线程模式使用 runBlocking 等待协程内清理完成，
     * 单线程模式直接同步清理，避免不必要的 runBlocking 阻塞主线程。
     */
    override fun close() {
        stepGate.beginClose()
        initialized = false
        try {
            if (singleThreadMode) {
                // ★ 单线程模式：直接同步清理，无需协程上下文
                if (::terrainManager.isInitialized) terrainManager.destroy()
                if (::blockShapeManager.isInitialized) {
                    blockShapeManager.SHAPE_CACHE.clear()
                }
                if (::world.isInitialized) world.destroy()
                hostManager.clear()
            } else {
                // 多线程模式：协程内清理
                runBlocking {
                    if (::terrainManager.isInitialized) terrainManager.destroy()
                    if (::blockShapeManager.isInitialized) {
                        blockShapeManager.SHAPE_CACHE.clear()
                    }
                    if (::world.isInitialized) world.destroy()
                    hostManager.clear()
                    scope.cancel("物理线程已关闭")
                    dispatcher.close()
                }
            }
    
        } finally { stepGate.unlock() }
    }

    /**
     * 重启并刷新线程。
     * 单线程模式下不支持自动重启（无协程故 handleException 永不触发），
     * 若被直接调用则记录警告并跳过。
     */
    fun restart() {
        if (singleThreadMode) {
            SparkCore.LOGGER.warn(
                "{} 单线程模式下不支持自动重启，跳过 restart()",
                name
            )
            return
        }
        close()
        start(initCallback)
        crashCount.set(0)
    }

    override fun prePhysicsTick(space: PhysicsSpace, timeStep: Float) {
        world.pcoList.forEach { pco -> pco.isColliding = false }
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Pre(this))
        processTasks(PPhase.ALL)
        processTasks(PPhase.PRE)
    }

    override fun physicsTick(space: PhysicsSpace, timeStep: Float) {
        NeoForge.EVENT_BUS.post(PhysicsLevelTickEvent.Post(this))
        processTasks(PPhase.ALL)
        processTasks(PPhase.POST)
    }

    /**
     * 异常处理
     */
    protected fun handleException(context: CoroutineContext, exception: Throwable) {
        SparkCore.LOGGER.error("物理线程崩溃！尝试重启...", exception)
        if (crashCount.incrementAndGet() < 3) {
            restart()
        } else {
            SparkCore.LOGGER.error("物理线程连续崩溃，停止恢复！")
        }
    }

    abstract fun requestEntities(): List<Entity>

}
