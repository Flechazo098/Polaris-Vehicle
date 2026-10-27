package com.flechazo.polaris_vehicle.client;

import com.flechazo.polaris_vehicle.vehicle.collision.OrientedBoundingBox;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.function.Predicate;

public final class VehicleFirstPersonView {
    private static final float VANILLA_PLAYER_SCALE = 0.9375F;
    private static final float VANILLA_MODEL_ROOT_HEIGHT = 1.501F;

    private VehicleFirstPersonView() {
    }

    public static Matrix4f lookingHeadTransform(Matrix4fc authoredHead, Quaternionfc bodyRotation,
                                                Quaternionfc cameraRotation) {
        Quaternionf look = new Quaternionf().rotateY((float) -Math.PI)
                .mul(new Quaternionf(bodyRotation).conjugate()).mul(cameraRotation).normalize();
        Vector3f pivot = authoredHead.getTranslation(new Vector3f());
        return new Matrix4f().translationRotate(pivot, look);
    }

    public static Vec3 eyeInVehicle(Matrix4fc headTransform, float vanillaEyeHeight, float headFrontZ) {
        float eyeAboveHeadPivot = vanillaEyeHeight / VANILLA_PLAYER_SCALE - VANILLA_MODEL_ROOT_HEIGHT;
        return eyeOnHead(headTransform, eyeAboveHeadPivot, headFrontZ);
    }

    public static Vec3 eyeOnHead(Matrix4fc headTransform, float eyeAboveHeadPivot, float headFrontZ) {
        Vector3f eye = headTransform.transformPosition(new Vector3f(0, eyeAboveHeadPivot, headFrontZ));
        return new Vec3(-eye.x, eye.y, -eye.z);
    }

    public static Vec3 eyeInWorld(Vec3 localEye, Vec3 renderOrigin, Quaternionfc bodyRotation) {
        Vector3f offset = bodyRotation.transform(new Vector3f(
                (float) localEye.x, (float) localEye.y, (float) localEye.z));
        return renderOrigin.add(offset.x, offset.y, offset.z);
    }
    public static Vec3 eyeInWorld(Vec3 localEye, Vec3 previousOrigin, Vec3 currentOrigin,
                                  float partialTick, Quaternionfc bodyRotation) {
        return eyeInWorld(localEye, previousOrigin.lerp(currentOrigin,
                Math.clamp(partialTick, 0.0F, 1.0F)), bodyRotation);
    }

    public static Vec3 passengerEyeAtDriverHeight(Vec3 passengerEye, Vec3 driverEye) {
        return new Vec3(passengerEye.x, driverEye.y, passengerEye.z);
    }

    public static Vec3 cameraEyeLocal(ResourceLocation type, Vec3 eye) {
        return VehicleDefinitions.SUV_ID.equals(type)
                ? eye.add(0, -1.0 / 16, 0) : eye;
    }

    public record ClearanceBox(Matrix4fc boneTransform, Vec3 minimum, Vec3 maximum) {
    }

    public static boolean intersectsBody(Vec3 eye, List<ClearanceBox> boxes, double radius) {
        Vector3f modelEye = new Vector3f((float) -eye.x, (float) eye.y, (float) -eye.z);
        for (ClearanceBox box : boxes) {
            Vector3f point = new Matrix4f(box.boneTransform()).invert()
                    .transformPosition(new Vector3f(modelEye));
            double distanceSquared = 0;
            for (int axis = 0; axis < 3; axis++) {
                double outside = Math.max(coordinate(box.minimum(), axis) - point.get(axis),
                        Math.max(0, point.get(axis) - coordinate(box.maximum(), axis)));
                distanceSquared += outside * outside;
            }
            if (distanceSquared <= radius * radius) return true;
        }
        return false;
    }

    public static boolean intersectsCamera(Vec3 eye, Matrix4fc headLook, List<ClearanceBox> boxes,
                                           double halfWidth, double halfHeight) {
        if (intersectsBody(eye, boxes, 0.005)) return true;
        var view = new Matrix4f(headLook).setTranslation((float) -eye.x, (float) eye.y, (float) -eye.z);
        var nearPlane = OrientedBoundingBox.transformed(new Vec3(0, 0, -0.05),
                new Vec3(halfWidth, halfHeight, 0.002), view);
        for (var box : boxes) {
            var body = OrientedBoundingBox.transformed(box.minimum().add(box.maximum()).scale(0.5),
                    box.maximum().subtract(box.minimum()).scale(0.5), new Matrix4f(box.boneTransform()));
            if (nearPlane.intersects(body)) return true;
        }
        return false;
    }

    public record Look(float yaw, float pitch) {
    }

    public static Look limitLook(Look previous, Look requested,
                                 Predicate<Look> blocked) {
        Look current = previous;
        if (blocked.test(current)) {
            for (float pitch = current.pitch(); pitch >= -90; pitch -= 0.5F) {
                Look candidate = new Look(current.yaw(), pitch);
                if (!blocked.test(candidate)) {
                    current = candidate;
                    break;
                }
            }
        }
        float yawDelta = requested.yaw() - current.yaw();
        float pitchDelta = requested.pitch() - current.pitch();
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(yawDelta), Math.abs(pitchDelta)) / 0.5));
        float yawStep = yawDelta / steps, pitchStep = pitchDelta / steps;
        for (int step = 0; step < steps; step++) {
            Look candidate = new Look(current.yaw() + yawStep, current.pitch() + pitchStep);
            if (!blocked.test(candidate)) {
                current = candidate;
                continue;
            }
            candidate = new Look(current.yaw() + yawStep, current.pitch());
            if (!blocked.test(candidate)) current = candidate;
            candidate = new Look(current.yaw(), current.pitch() + pitchStep);
            if (!blocked.test(candidate)) current = candidate;
        }
        return current;
    }

    private static double coordinate(Vec3 value, int axis) {
        return axis == 0 ? value.x : axis == 1 ? value.y : value.z;
    }
}
