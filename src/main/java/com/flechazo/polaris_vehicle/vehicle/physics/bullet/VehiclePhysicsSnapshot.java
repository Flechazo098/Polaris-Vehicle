package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Bullet result published back to the logical server thread.
 */
public record VehiclePhysicsSnapshot(
        Vec3 modelOrigin,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        Vec3 linearVelocity,
        Vec3 angularVelocity,
        List<Double> suspensionLengths,
        List<Boolean> wheelContacts,
        List<Vec3> wheelContactPoints,
        /** Central road-wheel angle in Bullet radians (+Y left), not raw A/D input. */
        double steeringAngle
) {

    public VehiclePhysicsSnapshot(Vec3 modelOrigin, float rotationX, float rotationY, float rotationZ,
                                  float rotationW, Vec3 linearVelocity, Vec3 angularVelocity, List<Double> suspensionLengths,
                                  List<Boolean> wheelContacts, List<Vec3> wheelContactPoints) {
        this(modelOrigin, rotationX, rotationY, rotationZ, rotationW, linearVelocity, angularVelocity,
                suspensionLengths, wheelContacts, wheelContactPoints, 0.0);
    }

    public VehiclePhysicsSnapshot {
        if (!Double.isFinite(steeringAngle) || Math.abs(steeringAngle) >= Math.PI / 2) {
            throw new IllegalArgumentException("Invalid steering angle");
        }
        Objects.requireNonNull(modelOrigin, "modelOrigin");
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        if (suspensionLengths.size() < 3
                || suspensionLengths.size() > 4
                || wheelContacts.size() != suspensionLengths.size()
                || wheelContactPoints.size() != suspensionLengths.size()) {
            throw new IllegalArgumentException("A vehicle snapshot requires three or four aligned wheel results");
        }
        if (wheelContactPoints.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Wheel contact points may not contain null");
        }
        double quaternionLengthSquared = rotationX * rotationX
                + rotationY * rotationY
                + rotationZ * rotationZ
                + rotationW * rotationW;
        if (!Double.isFinite(quaternionLengthSquared) || quaternionLengthSquared < 1.0E-8) {
            throw new IllegalArgumentException("Snapshot rotation must be a finite quaternion");
        }
    }

    public boolean grounded() {
        return wheelContacts.stream().anyMatch(Boolean::booleanValue);
    }
}
