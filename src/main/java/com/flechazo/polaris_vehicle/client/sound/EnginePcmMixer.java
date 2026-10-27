package com.flechazo.polaris_vehicle.client.sound;

final class EnginePcmMixer {
    record Control(float rate, float band, float volume) {
        Control {
            if (!Float.isFinite(rate) || !Float.isFinite(band) || !Float.isFinite(volume)
                    || rate <= 0 || rate > 16 || band < 0 || band > 2 || volume < 0 || volume > 1) {
                throw new IllegalArgumentException("Invalid engine PCM control");
            }
        }
    }

    record Loop(float[] samples, int cycles, double phase) {
        Loop {
            if (samples.length < 2 || cycles <= 0 || !Double.isFinite(phase)) {
                throw new IllegalArgumentException("Invalid engine cycle loop");
            }
        }

        double at(double cycle) {
            double position = ((cycle + phase) % cycles) * samples.length / cycles;
            int index = (int) position;
            double fraction = position - index;
            return samples[index] + (samples[(index + 1) % samples.length] - samples[index]) * fraction;
        }
    }

    private final Loop[] loops;
    private final double[][] correlation;
    private final double cycleStep;
    private final double response;
    private final double timbreResponse;
    private final EnginePcmCalibration calibration;
    private final double[] calibratedGains = {1, 1, 1};
    private final double[] currentGains = {1, 1, 1};
    private final double[][] targetCorrelation = new double[3][3];
    private final double[] weights = {1, 0, 0};
    private double cycles, rate = 1, volume;

    EnginePcmMixer(Loop[] loops, double[][] correlation, int sampleRate, double baseHz,
                   EnginePcmCalibration calibration) {
        if (loops.length != 3 || sampleRate <= 0 || !Double.isFinite(baseHz) || baseHz <= 0) {
            throw new IllegalArgumentException("Invalid engine PCM bank");
        }
        this.loops = loops.clone();
        this.calibration = calibration;
        this.correlation = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                this.correlation[i][j] = Math.clamp(correlation[i][j], 0, 1);
            }
        cycleStep = baseHz / sampleRate;
        response = -Math.expm1(-1.0 / (.012 * sampleRate));
        timbreResponse = -Math.expm1(-1.0 / (.080 * sampleRate));
    }

    void render(short[] output, int count, Control target) {
        int lower = Math.min((int) target.band, 1);
        double angle = (target.band - lower) * Math.PI / 2;
        double lowWeight = Math.cos(angle), highWeight = Math.sin(angle);
        calibration.sample(target.rate, calibratedGains, targetCorrelation);
        for (int frame = 0; frame < count; frame++) {
            rate += (target.rate - rate) * response;
            volume += (target.volume - volume) * response;
            double value = 0, power = 0;
            for (int i = 0; i < 3; i++) {
                double goal = i == lower ? lowWeight : i == lower + 1 ? highWeight : 0;
                weights[i] += (goal - weights[i]) * timbreResponse;
                currentGains[i] += (calibratedGains[i] - currentGains[i]) * response;
                value += loops[i].at(cycles) * weights[i] * currentGains[i];
            }
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++) {
                        correlation[i][j] += (targetCorrelation[i][j] - correlation[i][j]) * response;
                    power += weights[i] * weights[j] * correlation[i][j];
                }
            value *= volume / Math.sqrt(Math.max(power, 1E-12));
            output[frame] = (short) Math.round(Math.clamp(value, -1, 1) * 32767);
            cycles += cycleStep * rate;
        }
    }
}
