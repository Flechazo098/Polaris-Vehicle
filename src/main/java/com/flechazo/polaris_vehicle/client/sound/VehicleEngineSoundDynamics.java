package com.flechazo.polaris_vehicle.client.sound;

final class VehicleEngineSoundDynamics {
    static final float IDLE_RPM_RATIO = 0.16F;
    private static final float REDLINE_RPM_RATIO = 0.94F;
    private static final float UPSHIFT_RPM = 0.90F;
    private static final float DOWNSHIFT_RPM = 0.42F;
    private static final int SHIFT_TICKS = 4; // 200ms clutch/ratio transition at 20Hz
    private static final int MIN_SHIFT_INTERVAL_TICKS = 11; // 550ms; longer than the shift itself
    private static final float RISE_RESPONSE = 0.30F;
    private static final float FALL_RESPONSE = 0.28F;

    private final int forwardGears;
    private int gear = 1;
    private int ticksSinceShift = MIN_SHIFT_INTERVAL_TICKS;
    private int shiftTick = SHIFT_TICKS;
    private float shiftFromRatio;
    private float ratio;
    private boolean reverse;
    private float rpmRatio = IDLE_RPM_RATIO;

    VehicleEngineSoundDynamics(int forwardGears) {
        if (forwardGears < 1) {
            throw new IllegalArgumentException("An engine sound profile needs at least one gear");
        }
        this.forwardGears = forwardGears;
        ratio = gearRatio(1);
        shiftFromRatio = ratio;
    }

    Output update(double speedRatio, float throttle) {
        return update(speedRatio, throttle, false);
    }

    Output update(double speedRatio, float driveThrottle, boolean reversing) {
        float speed = (float) Math.clamp(speedRatio, 0.0, 1.0);
        float load = Math.clamp(Math.abs(driveThrottle), 0.0F, 1.0F);
        if (reverse != reversing) {
            reverse = reversing;
            gear = 1;
            ratio = reversing ? 1 : gearRatio(1);
            shiftTick = SHIFT_TICKS;
            ticksSinceShift = MIN_SHIFT_INTERVAL_TICKS;
        }
        ticksSinceShift = Math.min(ticksSinceShift + 1, MIN_SHIFT_INTERVAL_TICKS);
        if (reversing || speed < .015F) {
            // Clutch disengagement at walking speed must not march through forward
            // downshifts while the vehicle is already stationary.
            gear = 1;
            ratio = reversing ? 1 : gearRatio(1);
            shiftTick = SHIFT_TICKS;
        } else if (shiftTick >= SHIFT_TICKS && ticksSinceShift >= MIN_SHIFT_INTERVAL_TICKS) {
            float connectedRpm = roadRpm(speed, gearRatio(gear));
            if (gear < forwardGears && connectedRpm > UPSHIFT_RPM) changeGear(gear + 1);
            else if (gear > 1 && connectedRpm < DOWNSHIFT_RPM
                    && roadRpm(speed, gearRatio(gear - 1)) < .76F) changeGear(gear - 1);
        }
        float shiftDip = 1;
        if (shiftTick < SHIFT_TICKS) {
            float progress = (float) ++shiftTick / SHIFT_TICKS;
            float eased = progress * progress * (3 - 2 * progress);
            ratio = shiftFromRatio + (gearRatio(gear) - shiftFromRatio) * eased;
            shiftDip -= .25F * (float) Math.sin(progress * Math.PI);
        }
        float targetRpm = Math.clamp(Math.max(roadRpm(speed, ratio), IDLE_RPM_RATIO + load * .24F),
                IDLE_RPM_RATIO, REDLINE_RPM_RATIO);
        float response = targetRpm >= rpmRatio ? RISE_RESPONSE : FALL_RESPONSE;
        rpmRatio += Math.clamp((targetRpm - rpmRatio) * response, -.045F, .055F);
        return new Output(rpmRatio, load * shiftDip, gear);
    }

    private float gearRatio(int selectedGear) {
        // Equal fractional steps bound each upshift's RPM drop. Top gear reaches
        // redline at the performance limit.
        return forwardGears == 1 ? 1 : (float) Math.pow(3.2, (double) (forwardGears - selectedGear) / (forwardGears - 1));
    }

    private static float roadRpm(float speed, float ratio) {
        return IDLE_RPM_RATIO + speed * ratio * (REDLINE_RPM_RATIO - IDLE_RPM_RATIO);
    }

    private void changeGear(int selectedGear) {
        shiftFromRatio = ratio;
        gear = selectedGear;
        shiftTick = 0;
        ticksSinceShift = 0;
    }

    record Output(float rpmRatio, float load, int gear) {
    }
}
