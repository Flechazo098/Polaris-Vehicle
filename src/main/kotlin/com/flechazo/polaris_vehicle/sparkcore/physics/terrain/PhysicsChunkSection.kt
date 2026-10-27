// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: physics/terrain/PhysicsChunkSection.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.physics.terrain

import java.util.concurrent.atomic.AtomicLong

import com.flechazo.polaris_vehicle.sparkcore.SparkCore
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost
import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups
import com.flechazo.polaris_vehicle.sparkcore.physics.body.addPhysicsBody
import com.flechazo.polaris_vehicle.sparkcore.physics.body.owner
import com.flechazo.polaris_vehicle.sparkcore.physics.body.removePhysicsBody
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel
import com.flechazo.polaris_vehicle.sparkcore.util.BlockCollisionUtil
import com.flechazo.polaris_vehicle.sparkcore.util.PPhase
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.bullet.util.NativeLibrary
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import jme3utilities.math.MyMath
import kotlinx.coroutines.*
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import kotlin.math.floor

/**
 * 代表一个16x16x16区块section的物理表示
 * 每个非纯空气的section持有一个静态刚体，使用CompoundShape组合所有方块形状
 *
 * @property sectionPos 该section在世界中的坐标
 * @property physicsLevel 所属物理世界
 * @property chunk 对应的区块对象，用于获取方块状态
 */
class PhysicsChunkSection(
    val sectionPos: SectionPos,
    override val physicsLevel: PhysicsLevel,
    val chunk: LevelChunk
) : PhysicsHost {
    val blockMerger = BlockMerger(physicsLevel)
    // 缓存section内所有方块的BlockState，避免物理线程中的getBlockState调用
    private val buildGeneration = AtomicLong()
    private var committedGeneration = -1L
    private val buildLock = Any()
    private var buildInFlight = false
    private var pendingBuild: Pair<Long, SectionSnapshot>? = null
    @Volatile private var disposed = false
    @Volatile
    private var snapshotForCollision: SectionSnapshot? = null
    @Volatile
    private var lastSlipEpoch: Int = Int.MIN_VALUE
    @Volatile
    private var collisionShape: CompoundCollisionShape? = null
    @Volatile
    var physicsBody: PhysicsRigidBody? = null  // 公开以支持 Java 端 DDA 补充命中条目时获取刚体引用
    override val allPhysicsBodies: MutableMap<String, PhysicsCollisionObject> = mutableMapOf()
    @Volatile
    var isActive: Boolean = false
        private set

    /** Whether this section has been asked to build at all (see PhysicsChunk.requestBuild). */
    @Volatile
    var buildRequested: Boolean = false
        private set

    fun requestBuild(manager: PhysicsChunkManager) {
        if (buildRequested || disposed) return
        buildRequested = true
        startAsyncBuild(manager)
    }

    // 新增：构建状态管理
    enum class BuildState {
        IDLE,           // 空闲
        BUILDING,       // 构建中
        BUILT,          // 已构建
        FAILED          // 构建失败
    }

    @Volatile
    private var buildState = BuildState.IDLE
    private var buildJob: Job? = null

    /**
     * 异步构建碰撞形状
     * 在专用线程池中执行，避免阻塞主线程
     */


    /**
     * 从缓存的区块中预加载所有方块状态并构建碰撞形状
     * 使用洪水填充算法合并相同形状的方块
     */
    private fun buildCollisionShape(snapshot: SectionSnapshot): CompoundCollisionShape? {
        val compoundShape = CompoundCollisionShape()
        var hasCollision = false
        if (snapshot == SectionSnapshot.EMPTY) return null

        // 按Y层处理
        for (levelY in 0 until 16) {
            // 准备当前层的方块状态数组
            val layerBlocks = Array(16) { Array<BlockState?>(16) { null } }

            for (x in 0 until 16) {
                for (z in 0 until 16) {
                    val blockSnapshot = snapshot.getBlockSnapshot(x, levelY, z)
                    if (blockSnapshot != null) {
                        layerBlocks[x][z] = blockSnapshot.state
                    }
                }
            }

            // 合并当前层的方块
            val mergedRects = blockMerger.mergeLayer(levelY, layerBlocks)

            // 添加合并后的形状
            for (rect in mergedRects) {
                val collisionShape = rect.toCollisionShape()
                val centerPos = rect.getCenter(levelY)
                compoundShape.addChildShape(collisionShape, centerPos)
                hasCollision = true
            }

            // 处理无法合并的复杂形状
            for (x in 0 until 16) {
                for (z in 0 until 16) {
                    val blockSnapshot = snapshot.getBlockSnapshot(x, levelY, z)
                    if (blockSnapshot != null) {
                        // 如果是复杂形状且没有被合并过
                        if (!physicsLevel.blockShapeManager.isMergeableShape(blockSnapshot.state)) {
                            val shape = blockSnapshot.state.getBulletCollisionShape(physicsLevel)
                            addComplexShape(compoundShape, x, levelY, z, shape)
                            hasCollision = true
                        }
                    }
                }
            }
        }

        return if (hasCollision) compoundShape else null
    }

    /**
     * 添加复杂形状（保持原有逻辑）
     */
    private fun addComplexShape(
        compoundShape: CompoundCollisionShape,
        x: Int, y: Int, z: Int,
        shapeToAdd: CollisionShape
    ) {
        val relativePos = Vector3f(
            x + 0.5f - 8f,
            y + 0.5f - 8f,
            z + 0.5f - 8f
        )

        // 处理复合形状：如果是复合形状，需要拆分为基本形状
        if (shapeToAdd is CompoundCollisionShape) {
            // 遍历复合形状的所有子形状
            val children = shapeToAdd.listChildren()
            for (childShape in children) {
                val shape = childShape.shape
                val childTransform = childShape.copyTransform(null)

                // 合并变换：方块位置 + 子形状相对位置
                val combinedTransform =
                    MyMath.combine(Transform(relativePos, Quaternion.IDENTITY), childTransform, null)

                // 添加子形状到最终的复合形状
                compoundShape.addChildShape(shape, combinedTransform)
            }
        } else {
            // 对于非复合形状，直接添加
            compoundShape.addChildShape(shapeToAdd, Transform(relativePos, Quaternion.IDENTITY))
        }
    }









    /**
     * 检查是否正在构建中
     */
    fun isBuilding(): Boolean = buildState == BuildState.BUILDING

    /**
     * 检查是否已构建完成
     */
    fun isBuilt(): Boolean = physicsBody != null && collisionShape != null





    /**
     * 获取指定世界位置的方块状态(物理线程调用)
     */
    fun getBlockState(worldPos: BlockPos): BlockState? {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return null
        return getBlockSnapshot(worldPos)?.state
    }

    /**
     * 获取指定世界位置的方块信息(物理线程调用)
     */
    fun getBlockSnapshot(worldPos: BlockPos): SectionSnapshot.BlockSnapshot? {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return null
        return snapshotForCollision!!.getBlockSnapshotFromWorld(worldPos)
    }

    /**
     * 根据碰撞点获取方块位置
     */
    fun getBlockPosFromContactPoint(worldContactPoint: Vector3f, normal: Vector3f, distance: Float): BlockPos {
        val pos = worldContactPoint.add(normal.mult(distance))
        return BlockPos(
            floor(pos.x.toDouble()).toInt(),
            floor(pos.y.toDouble()).toInt(),
            floor(pos.z.toDouble()).toInt()
        )
    }

    /**
     * 标记指定位置的方块为已移除(物理线程调用)
     */
    fun markRemoved(worldPos: BlockPos) {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return
        if (isRemoved(worldPos)) return
        val pos = snapshotForCollision!!.getRelativePos(worldPos)
        val index = SectionSnapshot.getIndexFromRelativePos(pos.x, pos.y, pos.z)
        snapshotForCollision!!.blockSnapshots[index] = null
    }

    /**
     * 判断指定位置的方块是否已被标记移除(物理线程调用)
     */
    fun isRemoved(worldPos: BlockPos): Boolean {
        if (isEmpty() || snapshotForCollision == null || snapshotForCollision?.isEmpty() == true) return true
        return snapshotForCollision!!.getBlockSnapshotFromWorld(worldPos) == null
    }





    /**
     * 检查section是否为空（没有碰撞体积）
     */
    fun isEmpty(): Boolean = collisionShape == null

    /**
     * 仅刷新 snapshot 中的 slip，不触发 shape 重建和刚体重建
     */
    fun refreshSlipIfNeeded(epoch: Int) {
        if (lastSlipEpoch == epoch) return
        val snapshot = snapshotForCollision
        if (snapshot == null || snapshot.isEmpty()) {
            lastSlipEpoch = epoch
            return
        }

        val snapshots = snapshot.blockSnapshots
        for (index in snapshots.indices) {
            val blockSnapshot = snapshots[index] ?: continue
            val relativePos = SectionSnapshot.getRelativePosFromIndex(index)
            val worldPos = snapshot.getWorldPos(relativePos)
            val newSlip = BlockCollisionUtil.getSlip(chunk, blockSnapshot.state, worldPos)
            if (newSlip != blockSnapshot.slip) {
                snapshots[index] = blockSnapshot.withSlip(newSlip)
            }
        }
        lastSlipEpoch = epoch
    }

    private fun replaceCollisionSnapshot(source: SectionSnapshot?) {
        snapshotForCollision = source?.copy()
        lastSlipEpoch = Int.MIN_VALUE
    }

    fun startAsyncBuild(manager: PhysicsChunkManager) = scheduleBuild(manager)
    fun startAsyncUpdate(manager: PhysicsChunkManager) = scheduleBuild(manager)

    private fun scheduleBuild(manager: PhysicsChunkManager) {
        if (disposed) return
        val snapshot = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
        synchronized(buildLock) {
            if (disposed) return
            pendingBuild = buildGeneration.incrementAndGet() to snapshot
            if (buildInFlight) return
            buildInFlight = true
            buildState = BuildState.BUILDING
            buildJob = manager.terrainBuilderScope.launch {
                try {
                    while (true) {
                        val request = synchronized(buildLock) {
                            val next = pendingBuild
                            pendingBuild = null
                            if (next == null) buildInFlight = false
                            next
                        } ?: break
                        val (generation, source) = request
                        val shape = buildCollisionShape(source)
                        ensureActive()
                        physicsLevel.submitImmediateTask(PPhase.PRE) {
                            // A newer REQUEST need not starve the first usable shape.
                            // Only a newer COMMIT or destruction invalidates this result.
                            if (!disposed && generation > committedGeneration) {
                                commitBuild(source, shape)
                                committedGeneration = generation
                                buildState = if (shape != null) BuildState.BUILT else BuildState.IDLE
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (!disposed) buildState = BuildState.FAILED
                    SparkCore.LOGGER.error("Building section $sectionPos failed", failure)
                    synchronized(buildLock) { buildInFlight = false }
                }
            }
        }
    }

    /** Called on the physics thread, never by a terrain builder. */
    private fun commitBuild(snapshot: SectionSnapshot, shape: CompoundCollisionShape?) {
        var body = physicsBody
        if (body != null && physicsLevel.world.contains(body)) physicsLevel.world.removeCollisionObject(body)
        if (shape == null) {
            physicsBody = null
            collisionShape = null
            replaceCollisionSnapshot(snapshot)
            return
        }
        if (body == null) {
            body = PhysicsRigidBody(shape, 0f).apply {
                name = "section_$sectionPos"
                owner = this@PhysicsChunkSection
                setPhysicsLocation(Vector3f(sectionPos.minBlockX() + 8f,
                    sectionPos.minBlockY() + 8f, sectionPos.minBlockZ() + 8f))
                collisionGroup = CollisionGroups.TERRAIN
                setCollideWithGroups(CollisionGroups.NONE)
            }
        } else {
            // Detach first to discard cached collision algorithms for the old compound.
            body.collisionShape = shape
        }
        collisionShape = shape
        physicsBody = body
        replaceCollisionSnapshot(snapshot)
        if (isActive && !disposed) physicsLevel.world.addCollisionObject(body)
    }

    fun cancelBuild() {
        synchronized(buildLock) {
            committedGeneration = buildGeneration.incrementAndGet()
            pendingBuild = null
            buildJob?.cancel()
            buildJob = null
            buildInFlight = false
        }
        if (buildState == BuildState.BUILDING) buildState = BuildState.IDLE
    }

    fun activate() {
        if (disposed || isActive) return
        isActive = true
        refreshSlipIfNeeded(physicsLevel.terrainManager.currentWeatherEpoch())
        physicsLevel.submitImmediateTask(PPhase.PRE) {
            val body = physicsBody
            if (isActive && !disposed && body != null && !physicsLevel.world.contains(body))
                physicsLevel.world.addCollisionObject(body)
        }
    }

    fun deactivate() {
        if (!isActive) return
        isActive = false
        physicsLevel.submitImmediateTask(PPhase.PRE) {
            val body = physicsBody
            if (!isActive && body != null && physicsLevel.world.contains(body))
                physicsLevel.world.removeCollisionObject(body)
        }
    }

    fun destroy() {
        disposed = true
        isActive = false
        cancelBuild()
        physicsLevel.submitImmediateTask(PPhase.PRE) {
            val body = physicsBody
            if (body != null && physicsLevel.world.contains(body)) physicsLevel.world.removeCollisionObject(body)
            physicsBody = null
            collisionShape = null
            snapshotForCollision = null
            lastSlipEpoch = Int.MIN_VALUE
            allPhysicsBodies.clear()
        }
    }
}
