// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
// Extracted from SparkCore 1.0.1031: event/PhysicsContactEvent.kt
// Polaris changes are reproducible in tools/extract_sparkcore_physics.py.
package com.flechazo.polaris_vehicle.sparkcore.event

import com.flechazo.polaris_vehicle.sparkcore.physics.body.ManifoldPoint
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.neoforged.bus.api.Event

/**
 * ### ��ײ���������¼�
 *
 * ���¼�����ײ�Ӵ������ʱ������ʹ��[com.jme3.bullet.collision.ManifoldPoints]������[manifoldId]��ȡ��Ҫ�ĽӴ������Ϣ
 */
abstract class PhysicsContactEvent(val manifoldId: Long): Event() {

    class Start(manifoldId: Long): PhysicsContactEvent(manifoldId)

    class Process(val o1: PhysicsCollisionObject, val o2: PhysicsCollisionObject, val o1Point: ManifoldPoint, val o2Point: ManifoldPoint, manifoldId: Long): PhysicsContactEvent(manifoldId)

    class End(manifoldId: Long): PhysicsContactEvent(manifoldId)

}