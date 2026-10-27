package com.flechazo.polaris_vehicle.vehicle.physics;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class VehicleSlipstreamTrail {

    private static final int MAX_SAMPLES = 60;

    private static final double TELEPORT_DISTANCE = 8.0;

    private final ArrayDeque<Vec3> samples = new ArrayDeque<>();

    public void record(Vec3 rearBumper) {
        Vec3 last = samples.peekFirst();
        if (last != null && last.distanceToSqr(rearBumper) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            samples.clear();
        }
        samples.addFirst(rearBumper);
        while (samples.size() > MAX_SAMPLES) {
            samples.removeLast();
        }
    }

    public List<Vec3> snapshot(Vec3 currentRearBumper) {
        List<Vec3> trail = new ArrayList<>(samples.size() + 1);
        trail.add(currentRearBumper);
        trail.addAll(samples);
        return trail;
    }
}
