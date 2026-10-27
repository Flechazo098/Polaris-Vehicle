package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.jme3.math.Vector3f;

/**
 * Per-wheel raw stair tier; the smoothed surface only determines physical support.
 */
final class VehicleClimbSupportReference {
    private static final double STEP_EPSILON = 0.04;
    private static final double CONTACT_TOLERANCE = 0.08;
    private VehicleClimbHeightField.Surface acceptedSurface;

    void clear() {
        acceptedSurface = null;
    }

    double resolve(
            VehicleClimbHeightField field,
            Vector3f wheelCenter,
            float radius,
            double maximumStepHeight
    ) {
        return resolve(field, wheelCenter, radius, maximumStepHeight, false);
    }

    double resolve(
            VehicleClimbHeightField field,
            Vector3f wheelCenter,
            float radius,
            double maximumStepHeight,
            boolean confirmedPhysicalSupport
    ) {
        double physicalBottom = wheelCenter.y - radius;
        VehicleClimbHeightField.Surface current = field.rawSurfaceAt(wheelCenter.x, wheelCenter.z);
        VehicleClimbHeightField.Contact support = field.solveSphereContact(
                wheelCenter, radius, wheelCenter
        );
        // A jumping wheel cannot promote the wall beneath it to a foothold.
        // Retain a raw tier only while the sphere is actually near its smooth
        // support surface. This also clears stale tiers on drops/teleports.
        // A real manifold from the immediately previous solve is stronger
        // evidence than predicted tangency for a rigid four-wheel chassis.
        if (current == null || (!confirmedPhysicalSupport
                && (!support.valid() || Math.abs(support.penetration()) > CONTACT_TOLERANCE))) {
            clear();
            return physicalBottom;
        }
        double previousReference = physicalBottom;
        if (acceptedSurface != null) {
            var previous = acceptedSurface.blockPos();
            var next = current.blockPos();
            if (Math.abs(previous.getX() - next.getX()) <= 1
                    && Math.abs(previous.getZ() - next.getZ()) <= 1) {
                previousReference = Math.max(previousReference, acceptedSurface.height());
            }
        }
        // Advance only after this wheel's centre has entered the next column,
        // and only one legal tier at a time. Never use a future contact point
        // or another wheel's tier as this wheel's reference.
        if (current.height() <= previousReference + maximumStepHeight + STEP_EPSILON) {
            acceptedSurface = current;
            return Math.max(physicalBottom, current.height());
        }
        clear();
        return physicalBottom;
    }
}
