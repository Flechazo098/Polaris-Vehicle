package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.Util;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VehicleEnginePcmBank implements ResourceManagerReloadListener {
    public static final VehicleEnginePcmBank INSTANCE = new VehicleEnginePcmBank();

    record Bank(EnginePcmMixer.Loop[] loops, double[][] correlation, int sampleRate, double baseHz,
                EnginePcmCalibration calibration) {
    }

    private final Map<VehicleEngineSoundProfile, CompletableFuture<Bank>> cache = new HashMap<>();
    private ResourceManager resources;

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resources) {
        this.resources = resources;
        cache.clear();
    }

    synchronized CompletableFuture<Bank> get(VehicleEngineSoundProfile profile) {
        ResourceManager source = resources;
        if (source == null) return CompletableFuture.failedFuture(new IOException("Engine PCM resources not loaded"));
        return cache.computeIfAbsent(profile, key -> CompletableFuture.supplyAsync(() ->
                Pathway.tryOf(() -> load(source, key))
                        .peekFailure(exception -> PolarisVehicle.LOGGER.error(
                                "Unable to prepare engine PCM: {} (retry after resource reload)", key, exception))
                        .run().fold(exception -> { throw new CompletionException(exception); }, bank -> bank),
                Util.nonCriticalIoPool()));
    }

    synchronized boolean failed(VehicleEngineSoundProfile profile) {
        var future = cache.get(profile);
        return future != null && future.isCompletedExceptionally();
    }

    private static Bank load(ResourceManager resources, VehicleEngineSoundProfile profile) throws IOException {
        var manifestPath = PolarisVehicle.id("sounds/vehicle/engine/bands.json");
        try (var reader = new InputStreamReader(resources.open(manifestPath), StandardCharsets.UTF_8)) {
            String name = profile.soundEvent().getPath().replace("vehicle.engine.", "");
            var manifest = EnginePcmManifest.read(reader, name);
            var entry = manifest.vehicle();
            var bands = entry.bands();
            var loops = new EnginePcmMixer.Loop[3];
            int rate = 0;
            for (int i = 0; i < 3; i++) {
                var band = bands.get(i);
                var path = PolarisVehicle.id(band.resource());
                try (var input = resources.open(path); var decoder = new JOrbisAudioStream(input)) {
                    var format = decoder.getFormat();
                    if (format.getChannels() != 1 || format.getSampleSizeInBits() != 16
                            || (rate != 0 && rate != (int) format.getSampleRate())) {
                        throw new IOException("Incompatible engine PCM format: " + path);
                    }
                    rate = (int) format.getSampleRate();
                    var pcm = decoder.readAll().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                    float[] samples = new float[pcm.remaining()];
                    for (int j = 0; j < samples.length; j++) samples[j] = pcm.get() / 32768F;
                    loops[i] = new EnginePcmMixer.Loop(samples, band.cycles(), band.phase());
                }
            }
            return new Bank(loops, entry.correlationMatrix(), rate, manifest.baseCycleHz(), entry.gainCalibration());
        }
    }
}
