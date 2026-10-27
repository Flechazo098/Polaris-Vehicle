package com.flechazo.polaris_vehicle.vehicle.physics;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class VehicleDriverPoseExtrapolator {
    private Vec3 positionStep = Vec3.ZERO;
    private float yawStep;
    private final Quaternionf rotationStep = new Quaternionf();
    private boolean stepValid;
    private boolean extrapolated;
    private boolean consumedLastTick;

    public void reset() {
        stepValid = false;
        extrapolated = false;
        consumedLastTick = false;
    }

    public void consumed(Vec3 previousPosition, float previousYaw, Quaternionf previousRotation,
                         Vec3 position, float yaw, Quaternionf rotation, int receivedSnapshots) {
        if (consumedLastTick) {
            int serverTicks = Math.max(1, receivedSnapshots);
            positionStep = position.subtract(previousPosition).scale(1.0 / serverTicks);
            yawStep = (yaw - previousYaw) / serverTicks;
            Quaternionf change = new Quaternionf(rotation).mul(new Quaternionf(previousRotation).conjugate());
            rotationStep.identity().slerp(change, 1.0F / serverTicks);
            stepValid = true;
        }
        extrapolated = false;
        consumedLastTick = true;
    }

    public boolean gap(boolean localDriver) {
        consumedLastTick = false;
        if (!localDriver || !stepValid || extrapolated) return false;
        extrapolated = true;
        return true;
    }

    public Vec3 positionStep() {
        return positionStep;
    }

    public float yawStep() {
        return yawStep;
    }

    public Quaternionf rotate(Quaternionf current) {
        return new Quaternionf(rotationStep).mul(current);
    }
}
