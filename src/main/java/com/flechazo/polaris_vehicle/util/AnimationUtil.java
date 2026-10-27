package com.flechazo.polaris_vehicle.util;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.AnimationVector;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.HumanoidAnimationPose;
import com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidAnimations.HumanoidBoneTransform;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.loading.math.MathValue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class AnimationUtil {
    private static final HumanoidAnimationPose EMPTY_POSE = new HumanoidAnimationPose(Map.of());

    private AnimationUtil() {
    }

    public static HumanoidAnimationPose turnPose(Map<String, HumanoidAnimationPose> poses, boolean left) {
        String suffix = left ? "turn_left" : "turn_right";
        HumanoidAnimationPose humanoid = poses.get("animation.humanoid." + suffix);
        if (humanoid != null) return humanoid;
        String firstName = null;
        HumanoidAnimationPose firstPose = EMPTY_POSE;
        for (var entry : poses.entrySet()) {
            String name = entry.getKey();
            if (name.endsWith(suffix) && (firstName == null || name.compareTo(firstName) < 0)) {
                firstName = name;
                firstPose = entry.getValue();
            }
        }
        return firstPose;
    }

    public static HumanoidAnimationPose blendPose(HumanoidAnimationPose neutral,
                                                  HumanoidAnimationPose endpoint, float amount) {
        float t = Math.clamp(amount, 0, 1);
        if (t == 0) return neutral;
        if (t == 1) return endpoint;
        Map<String, HumanoidBoneTransform> blended = new LinkedHashMap<>();
        var names = new LinkedHashSet<>(neutral.bones().keySet());
        names.addAll(endpoint.bones().keySet());
        for (String name : names) {
            HumanoidBoneTransform zero = new HumanoidBoneTransform(Maybe.none(), Maybe.none());
            var start = neutral.bones().getOrDefault(name, zero);
            var end = endpoint.bones().getOrDefault(name, start);
            blended.put(name, new HumanoidBoneTransform(blendVector(start.rotation(), end.rotation(), t),
                    blendVector(start.position(), end.position(), t)));
        }
        return new HumanoidAnimationPose(blended);
    }

    private static Maybe<AnimationVector> blendVector(Maybe<AnimationVector> start,
                                                      Maybe<AnimationVector> end, float t) {
        if (!start.isDefined() && !end.isDefined()) return Maybe.none();
        var a = start.orElse(AnimationVector.ZERO);
        var b = end.orElse(a);
        return Maybe.some(new AnimationVector(a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t));
    }

    /**
     * Samples the baked easing curve and holds its final value after the clip ends.
     */
    public static float sampleKeyframes(List<Keyframe<MathValue>> frames, double tick) {
        if (frames.isEmpty()) return 0;
        double remaining = Math.max(0, tick);
        for (var frame : frames) {
            if (remaining < frame.length()) {
                return (float) EasingType.lerpWithOverride(new AnimationPoint(frame, remaining,
                        frame.length(), frame.startValue().get(), frame.endValue().get()), null);
            }
            remaining -= frame.length();
        }
        return (float) frames.getLast().endValue().get();
    }

    public static float shoulderY(float initialY, float authoredOffset) {
        return initialY - (initialY - 2.0F) - authoredOffset;
    }

    public static double approachPerTick(double current, double target, double elapsedTicks,
                                         double responsePerTick) {
        double factor = 1.0 - Math.pow(1.0 - responsePerTick, Math.max(0, elapsedTicks));
        return current + (target - current) * factor;
    }
}
