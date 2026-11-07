// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.physics.body;

import com.flechazo.polaris_vehicle.sparkcore.api.SparkLevel;
import com.flechazo.polaris_vehicle.sparkcore.physics.PhysicsHost;
import com.flechazo.polaris_vehicle.sparkcore.util.InlineEventConsumer;
import com.flechazo.polaris_vehicle.sparkcore.util.Subscription;
import com.jme3.bullet.collision.PhysicsCollisionObject;

import net.minecraft.world.level.Level;

import java.util.function.Predicate;

public final class PhysicsBodyExtension {
    public static PhysicsBodyState stateOf(PhysicsCollisionObject body) {
        return body.getPhysicsState();
    }

    public static PhysicsHost getOwner(PhysicsCollisionObject body) {
        return body.getUserObject() instanceof PhysicsHost host ? host : null;
    }

    public static void setOwner(PhysicsCollisionObject body, PhysicsHost owner) {
        var old = getOwner(body);
        var event = body.triggerEvent(new PhysicsBodyEvent.OwnerChanged(old, owner));
        var value = event.getNewOwner();
        if (value != null) value.getAllPhysicsBodies().put(body.name, body);
        else if (old != null) old.getAllPhysicsBodies().remove(body.name);
        body.setUserObject(value);
    }

    public static void addPhysicsBody(Level level, PhysicsCollisionObject body) {
        var physics = SparkLevel.getPhysicsLevel(level);
        physics.submitImmediateTask(() -> physics.getWorld().addCollisionObject(body));
    }

    public static void removePhysicsBody(Level level, PhysicsCollisionObject body) {
        var physics = SparkLevel.getPhysicsLevel(level);
        physics.submitImmediateTask(
                () -> {
                    setOwner(body, null);
                    if (body.isInWorld()) physics.getWorld().removeCollisionObject(body);
                });
    }

    public static Subscription onCollideStarted(
            PhysicsCollisionObject body,
            InlineEventConsumer<PhysicsBodyEvent.Collide.Started> handler) {
        return body.onEvent(PhysicsBodyEvent.Collide.Started.class, handler);
    }

    public static Subscription onCollideProcessed(
            PhysicsCollisionObject body,
            InlineEventConsumer<PhysicsBodyEvent.Collide.Processed> handler) {
        return body.onEvent(PhysicsBodyEvent.Collide.Processed.class, handler);
    }

    public static Subscription onCollideEnded(
            PhysicsCollisionObject body,
            InlineEventConsumer<PhysicsBodyEvent.Collide.Ended> handler) {
        return body.onEvent(PhysicsBodyEvent.Collide.Ended.class, handler);
    }

    public static Subscription onTick(
            PhysicsCollisionObject body, InlineEventConsumer<PhysicsBodyEvent.Tick> handler) {
        return body.onEvent(PhysicsBodyEvent.Tick.class, handler);
    }

    public static Subscription onPrePhysicsTick(
            PhysicsCollisionObject body,
            InlineEventConsumer<PhysicsBodyEvent.PhysicsTick.Pre> handler) {
        return body.onEvent(PhysicsBodyEvent.PhysicsTick.Pre.class, handler);
    }

    public static Subscription onPostPhysicsTick(
            PhysicsCollisionObject body,
            InlineEventConsumer<PhysicsBodyEvent.PhysicsTick.Post> handler) {
        return body.onEvent(PhysicsBodyEvent.PhysicsTick.Post.class, handler);
    }

    public static Subscription onCollidePre(
            PhysicsCollisionObject body, Predicate<PhysicsBodyEvent.Collide.Pre> handler) {
        return body.onEvent(
                PhysicsBodyEvent.Collide.Pre.class,
                event -> {
                    if (!handler.test(event)) event.setCanceled(true);
                });
    }
}
