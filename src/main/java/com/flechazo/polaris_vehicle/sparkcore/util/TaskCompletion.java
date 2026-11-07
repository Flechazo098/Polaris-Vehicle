package com.flechazo.polaris_vehicle.sparkcore.util;

import com.flechazo.hkt.business.util.ThrowableSupport;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class TaskCompletion {
    private TaskCompletion() {}

    public static void await(Future<?> future) throws InterruptedException {
        InterruptedException interruption = null;
        Throwable failure = null;
        try {
            while (true) {
                try {
                    future.get();
                    break;
                } catch (InterruptedException error) {
                    if (interruption == null) interruption = error;
                    else ThrowableSupport.suppress(interruption, error);
                } catch (ExecutionException error) {
                    failure = ThrowableSupport.unwrapAsync(error);
                    break;
                }
            }
        } finally {
            if (interruption != null) Thread.currentThread().interrupt();
        }
        if (failure != null) {
            if (interruption != null) ThrowableSupport.suppress(failure, interruption);
            if (failure instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            throw ThrowableSupport.propagate(failure);
        }
        if (interruption != null) throw interruption;
    }
}
