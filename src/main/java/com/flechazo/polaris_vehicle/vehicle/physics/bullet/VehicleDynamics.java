package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.util.MathUtil;

/**
 * Pure vehicle-force helpers kept independent from Bullet for deterministic tests.
 */
public final class VehicleDynamics {
    /**
     * PhysX vehicle default for the normalized load where lateral stiffness saturates.
     */
    public static final double DEFAULT_LATERAL_STIFFNESS_SATURATION_LOAD = 2.5;
    /**
     * PhysX vehicle documentation recommends roughly 18 rest-loads per radian.
     */
    public static final double DEFAULT_LATERAL_STIFFNESS_PER_REST_LOAD = 18.0;
    /**
     * PhysX low-speed denominator used when deriving lateral slip angle.
     */
    public static final double MINIMUM_LATERAL_MODEL_SPEED = 1.0;
    /**
     * Keep the friction-generated roll moment below the static tip-over
     * threshold.  A margin is intentional: transient suspension load and a
     * block edge can momentarily leave less than the full track supporting the
     * chassis.
     */
    public static final double ROLLOVER_MOMENT_MARGIN = 0.82;
    /**
     * Bullet's ray-cast vehicle uses a small non-zero roll influence too.
     */
    public static final double MINIMUM_ROLL_INFLUENCE = 0.10;
    /**
     * Tire carcass relaxation length used to avoid an instantaneous side load at walking speed.
     */
    public static final double LATERAL_RELAXATION_LENGTH_METERS = 0.75;
    private static final double MINIMUM_RELAXATION_SPEED = 1.5;

    private VehicleDynamics() {
    }

    public static double springStiffness(double sprungMass, double naturalFrequencyHertz) {
        double angularFrequency = Math.PI * 2.0 * naturalFrequencyHertz;
        return sprungMass * angularFrequency * angularFrequency;
    }

    public static double dampingCoefficient(
            double sprungMass,
            double springStiffness,
            double dampingRatio
    ) {
        return 2.0 * dampingRatio * Math.sqrt(sprungMass * springStiffness);
    }

    /**
     * Spring preload expressed as an equivalent compression distance. Vehicle
     * model wheel centers describe the normal loaded ride height, so the
     * spring must already support its share of gravity at zero visual travel.
     */
    public static double staticLoadCompression(
            double sprungMass,
            double springStiffness,
            double gravity
    ) {
        if (sprungMass <= 0.0 || springStiffness <= 0.0 || gravity <= 0.0) {
            return 0.0;
        }
        return sprungMass * gravity / springStiffness;
    }

    public static double suspensionForce(
            double compression,
            double compressionVelocity,
            double springStiffness,
            double compressionDamping,
            double reboundDamping
    ) {
        if (compression <= 0.0) {
            return 0.0;
        }
        double damping = compressionVelocity >= 0.0
                ? compressionDamping
                : reboundDamping;
        return Math.max(0.0, springStiffness * compression + damping * compressionVelocity);
    }

    /**
     * Rate at which a suspension shortens against a static support surface.
     * Velocity must be measured along the support normal, then converted to
     * travel along the suspension axis. Projecting world translation directly
     * onto a pitched chassis-up vector would falsely interpret forward speed
     * as suspension rebound.
     */
    public static double suspensionCompressionVelocity(
            double supportNormalVelocity,
            double supportNormalDotSuspensionUp
    ) {
        if (supportNormalDotSuspensionUp <= 1.0E-4) {
            return 0.0;
        }
        return -supportNormalVelocity / supportNormalDotSuspensionUp;
    }

    /**
     * Smooth wheel-centre climb from the lower surface to the step top.
     */
    public static double wheelStepArcHeight(
            double baseSurfaceY,
            double targetSurfaceY,
            double progress
    ) {
        double safeProgress = Math.clamp(progress, 0.0, 1.0);
        double blend = 0.5 - 0.5 * Math.cos(Math.PI * safeProgress);
        return baseSurfaceY + (targetSurfaceY - baseSurfaceY) * blend;
    }

    /**
     * Rise/run of {@link #wheelStepArcHeight} over a two-radius transition.
     */
    public static double wheelStepArcSlope(
            double stepHeight,
            double transitionHalfLength,
            double progress
    ) {
        if (stepHeight <= 0.0 || transitionHalfLength <= 0.0) {
            return 0.0;
        }
        double safeProgress = Math.clamp(progress, 0.0, 1.0);
        return stepHeight * Math.PI * Math.sin(Math.PI * safeProgress)
                / (4.0 * transitionHalfLength);
    }

    /**
     * A small physical tire needs a wider virtual roll path to climb a tall
     * Minecraft step. The final support height still uses its real radius.
     */
    public static double wheelStepArcHalfLength(double stepHeight, double tireRadius) {
        return Math.max(Math.max(0.0, tireRadius), Math.max(0.0, stepHeight) * 0.75);
    }

    /**
     * Radius of the temporary virtual tire. It must exceed the step height,
     * not merely half of it: only then is the sphere centre above the upper
     * corner and the contact normal contains an upward component.
     */
    public static double fullStepTireCollisionRadius(
            double stepHeight,
            double tireRadius
    ) {
        double safeRadius = Math.max(0.0, tireRadius);
        if (stepHeight < 0.875) {
            return safeRadius;
        }
        return Math.max(safeRadius, stepHeight * 1.05);
    }

    /**
     * Keeps the enlarged query sphere's bottom tangent at the real tire bottom.
     */
    public static double fullStepTireCenterLift(
            double stepHeight,
            double tireRadius
    ) {
        return fullStepTireCollisionRadius(stepHeight, tireRadius)
                - Math.max(0.0, tireRadius);
    }

    /**
     * Horizontal half-length used by the wheel-position constraint.
     */
    public static double wheelStepTransitionHalfLength(
            double stepHeight,
            double tireRadius
    ) {
        if (stepHeight >= 0.875) {
            double radius = fullStepTireCollisionRadius(stepHeight, tireRadius);
            double verticalOffset = radius - stepHeight;
            return Math.sqrt(Math.max(
                    0.0,
                    radius * radius - verticalOffset * verticalOffset
            ));
        }
        return wheelStepArcHalfLength(stepHeight, tireRadius);
    }

    /**
     * Real wheel-centre height produced by rolling the virtual tire over the upper corner.
     */
    public static double fullStepWheelCenterHeight(
            double baseSurfaceY,
            double targetSurfaceY,
            double realTireRadius,
            double progress
    ) {
        double stepHeight = Math.max(0.0, targetSurfaceY - baseSurfaceY);
        double virtualRadius = fullStepTireCollisionRadius(stepHeight, realTireRadius);
        double reach = wheelStepTransitionHalfLength(stepHeight, realTireRadius);
        double remaining = reach * (1.0 - Math.clamp(progress, 0.0, 1.0));
        double virtualCenterY = targetSurfaceY + Math.sqrt(Math.max(
                0.0,
                virtualRadius * virtualRadius - remaining * remaining
        ));
        return virtualCenterY - (virtualRadius - realTireRadius);
    }

    /**
     * Tangent rise/run of the virtual tire's circular corner path.
     */
    public static double fullStepTireArcSlope(
            double stepHeight,
            double realTireRadius,
            double progress
    ) {
        double virtualRadius = fullStepTireCollisionRadius(stepHeight, realTireRadius);
        double reach = wheelStepTransitionHalfLength(stepHeight, realTireRadius);
        double remaining = reach * (1.0 - Math.clamp(progress, 0.0, 1.0));
        double vertical = Math.sqrt(Math.max(
                1.0E-8,
                virtualRadius * virtualRadius - remaining * remaining
        ));
        return remaining / vertical;
    }

    /**
     * Height of a virtual straight ramp replacing one stair riser.  Extending
     * the run beyond one block keeps the slope steep enough to feel like block
     * stairs without presenting a near-vertical circular tangent at entry.
     */
    public static double stairRampWheelCenterHeight(
            double baseSurfaceY,
            double targetSurfaceY,
            double realTireRadius,
            double progress
    ) {
        double t = Math.clamp(progress, 0.0, 1.0);
        return baseSurfaceY
                + (targetSurfaceY - baseSurfaceY) * t
                + Math.max(0.0, realTireRadius);
    }

    /**
     * Rise/run of the straight virtual ramp used for full-block stairs.
     */
    public static double stairRampSlope(double stepHeight, double rampRun) {
        return Math.max(0.0, stepHeight) / Math.max(1.0E-6, rampRun);
    }

    /**
     * Progress along a stair ramp from signed distance to its vertical face.
     */
    public static double stairRampProgress(
            double distanceToFace,
            double leadDistance,
            double rampRun
    ) {
        return Math.clamp(
                (Math.max(0.0, leadDistance) - distanceToFace)
                        / Math.max(1.0E-6, rampRun),
                0.0,
                1.0
        );
    }

    /**
     * C1-continuous ramp height with cosine easing at both ends. The middle is
     * linear, while entry and exit slopes start/end at zero without a bump.
     */
    public static double smoothStairRampHeight(
            double totalRise,
            double totalRun,
            double easingLength,
            double distance
    ) {
        double rise = Math.max(0.0, totalRise);
        double run = Math.max(1.0E-6, totalRun);
        double x = Math.clamp(distance, 0.0, run);
        double easing = Math.clamp(easingLength, 0.0, run * 0.5);
        if (easing <= 1.0E-6) {
            return rise * x / run;
        }
        double maximumSlope = rise / (run - easing);
        if (x <= easing) {
            return maximumSlope * (0.5 * x
                    - easing / (2.0 * Math.PI)
                    * Math.sin(Math.PI * x / easing));
        }
        if (x < run - easing) {
            return maximumSlope * (x - easing * 0.5);
        }
        return rise - smoothStairRampHeight(
                rise,
                run,
                easing,
                run - x
        );
    }

    /**
     * First derivative of {@link #smoothStairRampHeight}.
     */
    public static double smoothStairRampSlope(
            double totalRise,
            double totalRun,
            double easingLength,
            double distance
    ) {
        double rise = Math.max(0.0, totalRise);
        double run = Math.max(1.0E-6, totalRun);
        double x = Math.clamp(distance, 0.0, run);
        double easing = Math.clamp(easingLength, 0.0, run * 0.5);
        if (easing <= 1.0E-6) {
            return rise / run;
        }
        double maximumSlope = rise / (run - easing);
        if (x <= easing) {
            return maximumSlope * 0.5
                    * (1.0 - Math.cos(Math.PI * x / easing));
        }
        if (x < run - easing) {
            return maximumSlope;
        }
        return smoothStairRampSlope(
                rise,
                run,
                easing,
                run - x
        );
    }

    public static ForcePair limitFrictionCircle(
            double longitudinalForce,
            double lateralForce,
            double maximumForce
    ) {
        double safeMaximum = Math.max(0.0, maximumForce);
        double magnitudeSquared = longitudinalForce * longitudinalForce
                + lateralForce * lateralForce;
        if (magnitudeSquared <= safeMaximum * safeMaximum || magnitudeSquared <= 1.0E-12) {
            return new ForcePair(longitudinalForce, lateralForce);
        }
        double scale = safeMaximum / Math.sqrt(magnitudeSquared);
        return new ForcePair(longitudinalForce * scale, lateralForce * scale);
    }

    /**
     * Equation 20 from the CarSimEd manual, as used by PhysX vehicles.
     */
    public static double physxSmoothingFunction(double value) {
        double safe = Math.max(0.0, value);
        return Math.min(
                1.0,
                safe - safe * safe / 3.0 + safe * safe * safe / 27.0
        );
    }

    public static double lateralSlipAngle(double lateralSpeed, double longitudinalSpeed) {
        return Math.atan(
                lateralSpeed
                        / (Math.abs(longitudinalSpeed) + MINIMUM_LATERAL_MODEL_SPEED)
        );
    }

    /**
     * Load-sensitive cornering stiffness used by the PhysX vehicle model.
     * The result has units of force per radian.
     */
    public static double lateralStiffness(
            double normalForce,
            double restLoad,
            double saturationNormalizedLoad,
            double stiffnessPerRestLoad
    ) {
        if (normalForce <= 0.0
                || restLoad <= 0.0
                || saturationNormalizedLoad <= 0.0
                || stiffnessPerRestLoad <= 0.0) {
            return 0.0;
        }
        double normalizedLoad = normalForce / restLoad;
        double stiffnessPosition = normalizedLoad * 3.0 / saturationNormalizedLoad;
        return restLoad
                * stiffnessPerRestLoad
                * physxSmoothingFunction(stiffnessPosition);
    }

    /**
     * Smooth combined-force saturation derived from the default PhysX tire
     * shader. Small requests remain linear; large requests approach the
     * friction boundary without a discontinuous clamp.
     */
    public static ForcePair physxTireForces(
            double requestedLongitudinalForce,
            double lateralSlipAngle,
            double lateralStiffness,
            double maximumForce
    ) {
        double requestedLateralForce = -lateralStiffness * Math.tan(lateralSlipAngle);
        double requestedMagnitude = Math.hypot(
                requestedLongitudinalForce,
                requestedLateralForce
        );
        double safeMaximum = Math.max(0.0, maximumForce);
        if (requestedMagnitude <= 1.0E-12 || safeMaximum <= 0.0) {
            return new ForcePair(0.0, 0.0);
        }
        double normalizedDemand = requestedMagnitude / safeMaximum;
        double deliveredMagnitude = safeMaximum
                * physxSmoothingFunction(normalizedDemand);
        double scale = deliveredMagnitude / requestedMagnitude;
        return new ForcePair(
                requestedLongitudinalForce * scale,
                requestedLateralForce * scale
        );
    }

    public static double steeringScale(double speed, double maximumSpeed) {
        return steeringScale(speed, maximumSpeed, 0.35, 0.25);
    }

    public static double steeringScale(
            double speed,
            double maximumSpeed,
            double limitStartRatio,
            double minimumScale
    ) {
        // A rational v² response, not a plateau followed by a speed gate.
        // The start ratio sets the characteristic (half attenuation)
        // speed. No curve knot exists at that speed or the engine's top speed.
        double characteristic = Math.max(1.0, 30.0
                * Math.max(0.05, Math.clamp(limitStartRatio, 0, 1)));
        double ratio = Math.abs(speed) / characteristic;
        double minimum = Math.clamp(minimumScale, 0, 1);
        // A small additional reduction at high road speeds. Smoothstep keeps
        // both ends free of a sudden steering change, while preserving the
        // configured minimum and all steering below 18 m/s.
        double highSpeedBlend = MathUtil.smoothstep((Math.abs(speed) - 18.0) / 12.0);
        return minimum + (1 - minimum) / (1 + ratio * ratio) * (1 - 0.10 * highSpeedBlend);
    }

    /**
     * Vertical friction-force application multiplier for a ray-cast wheel.
     *
     * <p>A ray-cast wheel has no physical tire body, so applying its complete
     * lateral force at the terrain hit point gives a tall chassis the full
     * centre-of-mass-to-ground lever arm. Bullet's vehicle implementation
     * avoids that artefact with {@code rollInfluence}. This version derives
     * the multiplier from the actual track, centre-of-mass height and tire
     * grip: at the friction limit the requested roll moment remains below the
     * static tip-over moment. Horizontal offsets are deliberately unaffected,
     * preserving steering yaw.</p>
     */
    public static double rollInfluence(
            double halfTrack,
            double centerOfMassHeight,
            double maximumGripCoefficient
    ) {
        if (halfTrack <= 0.0 || centerOfMassHeight <= 0.0
                || maximumGripCoefficient <= 0.0) {
            return 1.0;
        }
        double safe = ROLLOVER_MOMENT_MARGIN * halfTrack
                / (centerOfMassHeight * maximumGripCoefficient);
        return Math.clamp(safe, MINIMUM_ROLL_INFLUENCE, 1.0);
    }

    /**
     * First-order tire relaxation. At low speed this prevents binary steering
     * input from producing a full friction-circle side force in one physics
     * frame; response naturally becomes quicker as road speed rises.
     */
    public static double relaxLateralForce(
            double previousForce,
            double requestedForce,
            double wheelSpeed,
            double timeStepSeconds
    ) {
        if (timeStepSeconds <= 0.0) {
            return previousForce;
        }
        double relaxationSpeed = Math.max(Math.abs(wheelSpeed), MINIMUM_RELAXATION_SPEED);
        double response = 1.0 - Math.exp(
                -relaxationSpeed * timeStepSeconds / LATERAL_RELAXATION_LENGTH_METERS
        );
        return previousForce + (requestedForce - previousForce) * response;
    }

    public record ForcePair(double longitudinal, double lateral) {
    }
}
