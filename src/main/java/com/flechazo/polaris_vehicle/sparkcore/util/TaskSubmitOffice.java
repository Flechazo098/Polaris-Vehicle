// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public interface TaskSubmitOffice {
    ConcurrentHashMap<PPhase, ConcurrentHashMap<String, Runnable>> getTaskMap();

    ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<Runnable>> getImmediateQueue();

    ConcurrentHashMap<PPhase, ConcurrentHashMap<String, DelayedTask>> getDelayedTaskMap();

    ConcurrentHashMap<PPhase, ConcurrentLinkedDeque<DelayedTask>> getDelayedTaskQueue();

    int getTickCount();

    default void submitDeduplicatedTask(String key, PPhase phase, Runnable task) {
        getTaskMap()
                .compute(
                        phase,
                        (ignored, tasks) -> {
                            if (tasks == null) tasks = new ConcurrentHashMap<>();
                            tasks.put(key, task);
                            return tasks;
                        });
    }

    default void submitImmediateTask(PPhase phase, Runnable task) {
        getImmediateQueue()
                .computeIfAbsent(phase, ignored -> new ConcurrentLinkedDeque<>())
                .add(task);
    }

    default void submitImmediateTask(Runnable task) {
        submitImmediateTask(PPhase.ALL, task);
    }

    default void submitDelayedTask(String key, PPhase phase, int delayTicks, Runnable task) {
        getDelayedTaskMap()
                .computeIfAbsent(phase, ignored -> new ConcurrentHashMap<>())
                .put(key, new DelayedTask(getTickCount() + delayTicks, task));
    }

    default void submitDelayedTask(PPhase phase, int delayTicks, Runnable task) {
        getDelayedTaskQueue()
                .computeIfAbsent(phase, ignored -> new ConcurrentLinkedDeque<>())
                .add(new DelayedTask(getTickCount() + delayTicks, task));
    }

    default void processTasks(PPhase phase) {
        var tasks = getTaskMap().remove(phase);
        if (tasks != null) tasks.values().forEach(Runnable::run);
        var immediate = getImmediateQueue().get(phase);
        if (immediate != null) {
            Runnable task;
            while ((task = immediate.poll()) != null) task.run();
        }
        int now = getTickCount();
        var delayed = getDelayedTaskMap().get(phase);
        if (delayed != null) {
            var iterator = delayed.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var task = entry.getValue();
                if (now >= task.executeAtTick() && delayed.remove(entry.getKey(), task)) {
                    task.task().run();
                }
            }
        }
        var queue = getDelayedTaskQueue().get(phase);
        if (queue != null) {
            var iterator = queue.iterator();
            while (iterator.hasNext()) {
                var task = iterator.next();
                if (now >= task.executeAtTick()) {
                    iterator.remove();
                    task.task().run();
                }
            }
        }
    }
}
