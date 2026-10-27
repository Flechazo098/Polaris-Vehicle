package com.flechazo.polaris_vehicle.vehicle.physics;

import java.util.concurrent.locks.ReentrantLock;

public final class VehiclePhysicsStepGate {
    private final ReentrantLock lock = new ReentrantLock();
    private boolean pending;
    private boolean closed;

    public boolean tryPrepare() {
        if (!lock.tryLock()) return false;
        if (pending || closed) {
            lock.unlock();
            return false;
        }
        return true;
    }

    public void reserve() {
        if (!lock.isHeldByCurrentThread() || pending || closed)
            throw new IllegalStateException("Invalid physics batch reservation");
        pending = true;
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean beginInitialization() {
        lock.lock();
        if (closed) {
            lock.unlock();
            return false;
        }
        return true;
    }

    public void cancelReservation() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("Preparation lock required");
        pending = false;
    }

    public boolean beginStep() {
        lock.lock();
        if (closed || !pending) {
            lock.unlock();
            return false;
        }
        return true;
    }

    public void finishStep() {
        pending = false;
        lock.unlock();
    }

    public void beginClose() {
        lock.lock();
        closed = true;
        pending = false;
    }

    public void reopen() {
        lock.lock();
        try {
            if (pending) throw new IllegalStateException("Cannot reopen a running physics batch");
            closed = false;
        } finally {
            lock.unlock();
        }
    }
}
