package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import static com.flechazo.polaris_vehicle.util.MathUtil.smoothstep;

/**
 * Arcade corner grip on level ground; stair support and airborne physics stay unchanged.
 */
final class VehicleCorneringAssist {
    private VehicleCorneringAssist() {
    }

    static double multiplier(double speed, double steering, double upY, double minimumNormalY,
                             int supported, int installed) {
        if (installed < 3) return 1;
        double rolling = Math.abs(speed) * Math.abs(speed) / (speed * speed + 36);
        double upright = smoothstep((upY - 0.90) / 0.06);
        double level = smoothstep((minimumNormalY - 0.90) / 0.08);
        double support = smoothstep((double) supported / installed);
        double intent = smoothstep(Math.abs(steering) / 0.5);
        // No 20-28 m/s removal band: assistance remains predictable while the
        // v² curvature envelope handles high-speed stability independently.
        return 1 + 1.0 * rolling * upright * level * support * intent;
    }

    /**
     * Extra corner grip once fully up to speed (2026-10-20 playability request).
     */
    static final double HIGH_SPEED_GRIP_BONUS = 0.35;

    /**
     * Further arcade grip that builds in from 8 to 20 m/s, on top of
     * {@link #multiplier}. Full lock at top speed only managed about 42
     * deg/s, so fast corners felt like the car would not turn. Same
     * conditions as the base assist: level, upright, supported and steering.
     */
    static double highSpeedMultiplier(double speed, double steering, double upY, double minimumNormalY,
                                      int supported, int installed) {
        if (installed < 3) return 1;
        return 1 + HIGH_SPEED_GRIP_BONUS * smoothstep((Math.abs(speed) - 8) / 12)
                * smoothstep((upY - 0.90) / 0.06) * smoothstep((minimumNormalY - 0.90) / 0.08)
                * smoothstep((double) supported / installed) * smoothstep(Math.abs(steering) / 0.5);
    }

    static double responseMultiplier(double steering, double upY, double minimumNormalY,
                                     int supported, int installed) {
        if (installed < 3) return 1;
        return 1 + 1.5 * smoothstep(Math.abs(steering) / 0.5)
                * smoothstep((upY - 0.90) / 0.06) * smoothstep((minimumNormalY - 0.90) / 0.08)
                * smoothstep((double) supported / installed);
    }
}
