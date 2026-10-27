package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.Util;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VehicleHornPcmBank implements ResourceManagerReloadListener {
    public static final VehicleHornPcmBank INSTANCE = new VehicleHornPcmBank();

    record Phrase(float[] start, float[] loop, float[] tail, int sampleRate) {
        VehicleHornPhrase play() {
            return new VehicleHornPhrase(start, loop, tail, sampleRate);
        }
    }

    private final Map<String, CompletableFuture<Phrase>> cache = new HashMap<>();
    private ResourceManager resources;

    @Override
    public synchronized void onResourceManagerReload(ResourceManager resources) {
        this.resources = resources;
        cache.clear();
        VehicleHornSound.PHRASES.values().forEach(this::get);
    }

    synchronized CompletableFuture<Phrase> get(String name) {
        ResourceManager source = resources;
        if (source == null) return CompletableFuture.failedFuture(new IOException("Horn PCM resources not loaded"));
        return cache.computeIfAbsent(name, key -> CompletableFuture.supplyAsync(() ->
                Pathway.tryOf(() -> {
                    int[] rate = {0};
                    float[] start = decode(source, key + "_start", rate);
                    float[] loop = decode(source, key + "_loop", rate);
                    float[] tail = decode(source, key + "_tail", rate);
                    return new Phrase(start, loop, tail, rate[0]);
                }).peekFailure(exception -> PolarisVehicle.LOGGER.error(
                        "Unable to prepare horn PCM: {} (retry after resource reload)", key, exception))
                        .run().fold(exception -> { throw new CompletionException(exception); }, phrase -> phrase),
                Util.nonCriticalIoPool()));
    }

    private static float[] decode(ResourceManager resources, String file, int[] rate) throws IOException {
        var path = PolarisVehicle.id("sounds/vehicle/horn/" + file + ".ogg");
        try (var input = resources.open(path); var decoder = new JOrbisAudioStream(input)) {
            var format = decoder.getFormat();
            if (format.getChannels() != 1 || format.getSampleSizeInBits() != 16
                    || (rate[0] != 0 && rate[0] != (int) format.getSampleRate())) {
                throw new IOException("Horn parts must share one mono 16-bit format: " + path);
            }
            rate[0] = (int) format.getSampleRate();
            var pcm = decoder.readAll().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer();
            float[] samples = new float[pcm.remaining()];
            for (int i = 0; i < samples.length; i++) samples[i] = pcm.get() / 32768F;
            return samples;
        }
    }
}
