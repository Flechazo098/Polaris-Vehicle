package com.flechazo.polaris_vehicle.client.sound;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.io.Reader;
import java.util.List;
record EnginePcmManifest(double baseCycleHz, Vehicle vehicle) {
    private static final Codec<List<Double>> THREE_VALUES = Codec.DOUBLE.listOf(3, 3);
    private static final Codec<List<List<Double>>> CORRELATION = THREE_VALUES.listOf(3, 3);

    static EnginePcmManifest read(Reader reader, String profile) {
        var document = JsonParser.parseReader(reader).getAsJsonObject();
        var vehicle = Vehicle.CODEC.parse(JsonOps.INSTANCE,
                document.getAsJsonObject("vehicles").get(profile)).getOrThrow();
        return new EnginePcmManifest(document.get("base_cycle_hz").getAsDouble(), vehicle);
    }

    record Band(String resource, int cycles, double phase) {
        static final Codec<Band> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("resource").forGetter(Band::resource),
                Codec.INT.fieldOf("cycles").forGetter(Band::cycles),
                Codec.DOUBLE.fieldOf("phase").forGetter(Band::phase)
        ).apply(instance, Band::new));
    }

    record Vehicle(List<Band> bands, List<List<Double>> correlation,
                   List<CalibrationPoint> calibration) {
        static final Codec<Vehicle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Band.CODEC.listOf(3, 3).fieldOf("bands").forGetter(Vehicle::bands),
                CORRELATION.fieldOf("correlation").forGetter(Vehicle::correlation),
                CalibrationPoint.CODEC.listOf().fieldOf("calibration")
                        .forGetter(Vehicle::calibration)
        ).apply(instance, Vehicle::new));

        double[][] correlationMatrix() {
            return matrix(correlation);
        }

        EnginePcmCalibration gainCalibration() {
            return new EnginePcmCalibration(calibration.stream()
                    .map(CalibrationPoint::toPoint).toArray(EnginePcmCalibration.Point[]::new));
        }
    }

    record CalibrationPoint(double rate, List<Double> gains, List<List<Double>> correlation) {
        static final Codec<CalibrationPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("rate").forGetter(CalibrationPoint::rate),
                THREE_VALUES.fieldOf("gains").forGetter(CalibrationPoint::gains),
                CORRELATION.fieldOf("correlation").forGetter(CalibrationPoint::correlation)
        ).apply(instance, CalibrationPoint::new));

        EnginePcmCalibration.Point toPoint() {
            return new EnginePcmCalibration.Point(rate, vector(gains), matrix(correlation));
        }
    }

    private static double[] vector(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[][] matrix(List<List<Double>> rows) {
        return rows.stream().map(EnginePcmManifest::vector).toArray(double[][]::new);
    }
}
