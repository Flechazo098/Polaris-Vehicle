package com.flechazo.polaris_vehicle.vehicle.physics;

import java.util.ArrayDeque;

public final class VehicleSnapshotPlayback {

    public static <T> boolean prepare(ArrayDeque<T> snapshots, boolean started, boolean localDriver) {
        int startupDepth = localDriver ? 2 : 3;
        if (!started && snapshots.size() < startupDepth) return false;
        if (!started) while (snapshots.size() > startupDepth) snapshots.removeFirst();
        if (localDriver) {
            // A normal 2/0 arrival pair must play as two adjacent 50ms states.
            // Taking the newest state made the first tick jump twice as far,
            // then extrapolation ran ahead and the next correction held/snapped.
            // Discard old states only after a genuine backlog, retaining a
            // bounded buffer when a long render/network stall ends.
            while (snapshots.size() > 3) snapshots.removeFirst();
        } else if (snapshots.size() > 4) {
            snapshots.removeFirst();
        }
        return true;
    }
}
