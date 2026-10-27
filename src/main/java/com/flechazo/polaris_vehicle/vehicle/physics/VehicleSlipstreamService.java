package com.flechazo.polaris_vehicle.vehicle.physics;

import com.flechazo.polaris_vehicle.util.MathUtil;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class VehicleSlipstreamService {

    public static double bonus(
            VehicleSlipstreamProfile profile,
            Vec3 frontPosition,
            Vec3 frontForward,
            double frontSpeed,
            double frontRearExtent,
            Vec3 followerPosition,
            Vec3 followerForward,
            double followerFrontExtent
    ) {
        double speedBonus = profile.bonusForSpeed(frontSpeed);
        if (speedBonus <= 0.0) {
            return 0.0;
        }
        Vec3 frontDirection = horizontalUnit(frontForward);
        Vec3 followerDirection = horizontalUnit(followerForward);
        double alignment = frontDirection.dot(followerDirection);
        if (alignment < profile.minimumDirectionDot()) {
            return 0.0;
        }

        Vec3 offset = followerPosition.subtract(frontPosition);
        double behindDistance = -offset.dot(frontDirection);
        if (behindDistance <= 0.0) {
            return 0.0;
        }
        double gap = behindDistance - Math.max(0.0, frontRearExtent)
                - Math.max(0.0, followerFrontExtent) * alignment;
        if (gap > profile.maximumDistance()) {
            return 0.0;
        }
        Vec3 lateral = offset.add(frontDirection.scale(behindDistance));
        return lateral.horizontalDistance() <= profile.halfWidth() ? speedBonus : 0.0;
    }

    /**
     * Gap, in leader lengths, where the measured tow is strongest. CFD of two
     * F1 cars (Energies 13(19) 5183, 2020) found the follower's drag cut by
     * 40% at 0.25, 30.7% at 0.5, 21% at 1 and 14.2% at 2 car lengths.
     */
    private static final double FULL_TOW_LENGTHS = 0.25;
    /**
     * 1/(1 + x/k) through those points: k = 0.713 car lengths.
     */
    private static final double TOW_DECAY_LENGTHS = 0.713;
    /**
     * The wake still gives a third of its tow two car lengths back.
     */
    private static final double FULL_REACH_LENGTHS = 2.0;
    /**
     * The wake spreads as it travels: a quarter wider per car length.
     */
    private static final double SPREAD_PER_LENGTH = 0.25;
    /**
     * A nose tucked a little under the leader's tail is still in its wake.
     */
    private static final double TAIL_OVERLAP = 0.5;
    /**
     * Trail samples are a tick apart; this bridges the outside of a bend between two.
     */
    private static final double JOINT_SLACK = 0.5;
    /**
     * Anywhere in the wake gives at least half the tow; the measured falloff
     * lifts it from there to the full tow right behind the tail.
     */
    private static final double MINIMUM_TOW = 0.5;

    /**
     * Where the wake has faded out completely: the profile distance or two
     * leader lengths, whichever is longer, with the last third tapering off.
     */
    public static double wakeReach(VehicleSlipstreamProfile profile, double leaderLength) {
        return fadeStart(profile, leaderLength) * 1.5;
    }

    /**
     * How deep in the leader's wake the follower is: 1 right behind the tail,
     * falling off with the gap the way the measured tow does but never below
     * {@link #MINIMUM_TOW} inside the wake, 0 outside.
     *
     * <p>The wake follows the leader's actual path, not a straight line behind
     * its current heading. In a corner the follower drives where the leader
     * was a moment ago; a straight band from the leader's tail swings off
     * that line by d&sup2;/2R, about 2.4 blocks at 17 blocks in a 60-block
     * corner, and the bonus vanished unless the cars were nearly touching.</p>
     *
     * @param rearTrail    the leader's rear-bumper positions, newest (current) first
     * @param leaderLength bumper-to-bumper length of the leader
     * @param followerNose the follower's front-bumper position
     */
    public static double wakeStrength(
            VehicleSlipstreamProfile profile,
            List<Vec3> rearTrail,
            Vec3 frontForward,
            double leaderLength,
            Vec3 followerNose,
            Vec3 followerForward
    ) {
        if (rearTrail.isEmpty()) {
            return 0.0;
        }
        double length = Math.max(1.0, leaderLength);
        double reach = wakeReach(profile, length);
        Vec3 followerDirection = horizontalUnit(followerForward);
        Vec3 leaderDirection = horizontalUnit(frontForward);
        Vec3 newest = rearTrail.getFirst();
        // Alongside or ahead of the leader's tail is not in its wake.
        if (flat(followerNose.subtract(newest)).dot(leaderDirection) > TAIL_OVERLAP) {
            return 0.0;
        }
        List<Vec3> points = new ArrayList<>(rearTrail.size() + 1);
        points.add(newest);
        double recorded = 0.0;
        for (int i = 1; i < rearTrail.size() && recorded < reach; i++) {
            recorded += flat(rearTrail.get(i).subtract(points.getLast())).length();
            points.add(rearTrail.get(i));
        }
        // A trail shorter than the wake (just spawned, or slow) continues
        // straight back along the way it was heading.
        if (recorded < reach) {
            Vec3 back = leaderDirection.scale(-1.0);
            for (int i = points.size() - 1; i > 0; i--) {
                Vec3 step = flat(points.get(i).subtract(points.get(i - 1)));
                if (step.lengthSqr() > 1.0E-6) {
                    back = step.normalize();
                    break;
                }
            }
            points.add(points.getLast().add(back.scale(reach - recorded)));
        }
        double best = 0.0;
        double travelled = 0.0;
        for (int i = 1; i < points.size() && travelled < reach; i++) {
            Vec3 ahead = points.get(i - 1);
            Vec3 segment = flat(points.get(i).subtract(ahead));
            double segmentLength = segment.length();
            if (segmentLength < 1.0E-6) {
                continue;
            }
            Vec3 backward = segment.scale(1.0 / segmentLength);
            Vec3 offset = flat(followerNose.subtract(ahead));
            double along = offset.dot(backward);
            // Only beside this stretch of the path; a point past its end is
            // the next stretch's (or beyond the wake), and clamping it here
            // would count distance along the wake as distance across it.
            // The joint slack covers the outside of a bend between samples.
            if (along > segmentLength + JOINT_SLACK || along < (i == 1 ? -TAIL_OVERLAP : -JOINT_SLACK)) {
                travelled += segmentLength;
                continue;
            }
            double t = Math.clamp(along, 0.0, segmentLength);
            double gap = travelled + t;
            travelled += segmentLength;
            if (gap >= reach || followerDirection.dot(backward) > -profile.minimumDirectionDot()) {
                continue;
            }
            double lateral = offset.subtract(backward.scale(t)).length();
            double halfWidth = profile.halfWidth() * (1.0 + SPREAD_PER_LENGTH * gap / length);
            if (lateral >= halfWidth) {
                continue;
            }
            double across = lateral / halfWidth;
            best = Math.max(best, gapStrength(profile, gap, length) * (1.0 - across * across * across * across));
        }
        return best > 0.0 ? MINIMUM_TOW + (1.0 - MINIMUM_TOW) * best : 0.0;
    }

    private static double gapStrength(VehicleSlipstreamProfile profile, double gap, double length) {
        double tow = Math.min(1.0, (1.0 + FULL_TOW_LENGTHS / TOW_DECAY_LENGTHS)
                / (1.0 + gap / length / TOW_DECAY_LENGTHS));
        double fadeStart = fadeStart(profile, length);
        if (gap <= fadeStart) {
            return tow;
        }
        double t = Math.min(1.0, (gap - fadeStart) / (wakeReach(profile, length) - fadeStart));
        return tow * (1.0 - MathUtil.smoothstep(t));
    }

    private static double fadeStart(VehicleSlipstreamProfile profile, double leaderLength) {
        return Math.max(profile.maximumDistance(), FULL_REACH_LENGTHS * Math.max(1.0, leaderLength));
    }

    private static Vec3 flat(Vec3 value) {
        return new Vec3(value.x, 0.0, value.z);
    }

    private static Vec3 horizontalUnit(Vec3 value) {
        Vec3 horizontal = new Vec3(value.x, 0.0, value.z);
        return horizontal.lengthSqr() < 1.0E-9
                ? new Vec3(0.0, 0.0, 1.0)
                : horizontal.normalize();
    }
}
