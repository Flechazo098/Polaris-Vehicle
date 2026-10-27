package com.flechazo.polaris_vehicle.vehicle.seat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class VehicleSeatTransform {
    // LivingEntityRenderer translates the model by -1.501F after its Y flip.
    private static final float VANILLA_MODEL_ROOT_HEIGHT = 1.501F;

    private VehicleSeatTransform() {
    }

    public static Vec3 authoredRiderRenderTranslation(Vec3 authoredBodyPivot,
                                                      Vec3 seat, Vec3 riderAttachment, float playerScale) {
        if (!Float.isFinite(playerScale) || playerScale <= 0) {
            throw new IllegalArgumentException("Player render scale must be positive and finite");
        }
        Vec3 vanillaBodyPivot = seat.subtract(riderAttachment)
                .add(0, (double) VANILLA_MODEL_ROOT_HEIGHT * playerScale, 0);
        Vec3 delta = authoredBodyPivot.subtract(vanillaBodyPivot).scale(1.0 / playerScale);
        return new Vec3(-delta.x, delta.y, -delta.z);
    }

    public static Vec3 toBodySpace(Vec3 localSeat, Quaternionfc bodyRotation) {
        Vector3f rotated = bodyRotation.transform(new Vector3f(
                (float) localSeat.x,
                (float) localSeat.y,
                (float) localSeat.z
        ));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }

    public static Vec3 riderRootOffset(
            Vec3 localSeat,
            Vec3 localRiderAttachment,
            Quaternionfc bodyRotation
    ) {
        return riderPointOffset(
                localSeat,
                localRiderAttachment,
                Vec3.ZERO,
                bodyRotation
        );
    }

    public static Vec3 riderPointOffset(
            Vec3 localSeat,
            Vec3 localRiderAttachment,
            Vec3 localPointFromRiderRoot,
            Quaternionfc bodyRotation
    ) {
        return toBodySpace(
                localSeat.subtract(localRiderAttachment).add(localPointFromRiderRoot),
                bodyRotation
        );
    }

    public static Quaternionf riderRenderCorrection(
            Quaternionfc bodyRotation,
            float renderedBodyYawDegrees
    ) {
        Quaternionf vanillaOrientation = new Quaternionf()
                .rotateY((float) Math.toRadians(180.0F - renderedBodyYawDegrees));
        Quaternionf desiredOrientation = new Quaternionf(bodyRotation)
                .rotateY((float) Math.PI);
        return vanillaOrientation.conjugate().mul(desiredOrientation).normalize();
    }
}
