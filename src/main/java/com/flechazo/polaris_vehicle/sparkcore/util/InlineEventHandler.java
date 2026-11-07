// SPDX-License-Identifier: MIT
// Copyright (c) 2026 曦月 (SolarMoon) and SparkCore contributors.
package com.flechazo.polaris_vehicle.sparkcore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface InlineEventHandler<E extends InlineEvent> {
    Map<Class<? extends E>, List<InlineEventConsumer<? extends E>>> getEventHandlers();

    default <T extends E> Subscription onEvent(Class<T> type, InlineEventConsumer<T> handler) {
        getEventHandlers().computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
        return new Subscription(
                () -> {
                    var handlers = getEventHandlers().get(type);
                    if (handlers != null) handlers.remove(handler);
                });
    }

    @SuppressWarnings("unchecked")
    default <T extends E> T triggerEvent(T event) {
        boolean canceled = false;
        var handlers = getEventHandlers().get(event.getClass());
        if (handlers != null)
            for (var handler : List.copyOf(handlers)) {
                ((InlineEventConsumer<T>) handler).invoke(event);
                if (event instanceof CancellableEvent cancellable && cancellable.getCanceled())
                    canceled = true;
            }
        if (event instanceof CancellableEvent cancellable) cancellable.setCanceled(canceled);
        return event;
    }
}
