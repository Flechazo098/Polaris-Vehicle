package com.flechazo.polaris_vehicle.client.sound;

final class VehicleEngineSoundDriveState {
    private static final double STOP_SPEED = 0.10;

    static Output resolve(double signedSpeed, double forwardLimit, double reverseLimit,
                          float requestedThrottle, boolean explicitBrake) {
        float requested = Math.clamp(requestedThrottle, -1.0F, 1.0F);
        boolean opposingMotion = requested * signedSpeed < -STOP_SPEED;
        boolean braking = explicitBrake || opposingMotion;
        boolean reverse = signedSpeed < -STOP_SPEED
                || (Math.abs(signedSpeed) <= STOP_SPEED && requested < 0);
        double speedLimit = reverse ? reverseLimit : forwardLimit;
        double speedRatio = Math.clamp(Math.abs(signedSpeed) / Math.max(speedLimit, 1.0), 0.0, 1.0);
        return new Output(speedRatio, braking ? 0.0F : requested, reverse, braking);
    }

    record Output(double speedRatio, float driveThrottle, boolean reverse, boolean braking) {
    }

    static final class Transition {
        private int departureDirection;
        private boolean reverse;

        Output update(double signedSpeed, double forwardLimit, double reverseLimit,
                      float requestedThrottle, boolean explicitBrake) {
            var raw = resolve(signedSpeed, forwardLimit, reverseLimit, requestedThrottle, explicitBrake);
            int requestedDirection = (int) Math.signum(requestedThrottle);
            if (signedSpeed > .20) reverse = false;
            else if (signedSpeed < -.20) reverse = true;
            if (requestedDirection == 0 || (departureDirection != 0 && departureDirection != requestedDirection)) {
                departureDirection = 0;
            }
            if (raw.braking() && requestedDirection != 0 && signedSpeed * requestedDirection < -STOP_SPEED) {
                departureDirection = requestedDirection;
            }

            float throttle = raw.driveThrottle();
            if (departureDirection != 0) {
                double departureSpeed = signedSpeed * departureDirection;
                float progress = (float) Math.clamp((departureSpeed - STOP_SPEED) / 1.10, 0, 1);
                throttle *= progress * progress * (3 - 2 * progress);
                if (progress >= 1) departureDirection = 0;
            }
            double limit = reverse ? reverseLimit : forwardLimit;
            return new Output(Math.clamp(Math.abs(signedSpeed) / Math.max(limit, 1), 0, 1),
                    throttle, reverse, raw.braking());
        }
    }
}
