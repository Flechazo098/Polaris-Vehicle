package com.flechazo.polaris_vehicle.client.sound;

final class EnginePcmCalibration {
    record Point(double rate, double[] gains, double[][] correlation) {
    }

    private final Point[] points;

    EnginePcmCalibration(Point[] points) {
        if (points.length == 0) throw new IllegalArgumentException("Empty engine calibration");
        this.points = new Point[points.length];
        double previous = 0;
        for (int i = 0; i < points.length; i++) {
            var point = points[i];
            if (!Double.isFinite(point.rate) || point.rate <= previous || point.gains.length != 3
                    || point.correlation.length != 3)
                throw new IllegalArgumentException("Invalid engine calibration point");
            double[][] matrix = new double[3][3];
            for (int row = 0; row < 3; row++) {
                if (!Double.isFinite(point.gains[row]) || point.gains[row] < .7 || point.gains[row] > 1.42
                        || point.correlation[row].length != 3)
                    throw new IllegalArgumentException("Invalid engine gain calibration");
                for (int col = 0; col < 3; col++) {
                    double value = point.correlation[row][col];
                    if (!Double.isFinite(value) || Math.abs(value) > 1.00001)
                        throw new IllegalArgumentException("Invalid engine correlation");
                    matrix[row][col] = Math.clamp(value, -1, 1);
                }
            }
            this.points[i] = new Point(point.rate, point.gains.clone(), matrix);
            previous = point.rate;
        }
    }

    void sample(double rate, double[] gains, double[][] matrix) {
        int upper = 0;
        while (upper < points.length - 1 && rate > points[upper].rate) upper++;
        int lower = Math.max(0, upper - 1);
        var a = points[lower];
        var b = points[upper];
        double blend = lower == upper ? 0 : Math.clamp((rate - a.rate) / (b.rate - a.rate), 0, 1);
        for (int row = 0; row < 3; row++) {
            gains[row] = a.gains[row] + (b.gains[row] - a.gains[row]) * blend;
            for (int col = 0; col < 3; col++) {
                matrix[row][col] = a.correlation[row][col] + (b.correlation[row][col] - a.correlation[row][col]) * blend;
            }
        }
    }
}
