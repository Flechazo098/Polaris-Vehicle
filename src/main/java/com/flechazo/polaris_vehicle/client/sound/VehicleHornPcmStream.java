package com.flechazo.polaris_vehicle.client.sound;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.BooleanSupplier;

final class VehicleHornPcmStream implements AudioStream, VehicleShortBlockPcmStream {
    private final VehicleHornPhrase phrase;
    private final BooleanSupplier held;
    private final AudioFormat format;
    private ByteBuffer buffer;
    private float[] samples;
    private boolean closed;

    VehicleHornPcmStream(VehicleHornPhrase phrase, int sampleRate, BooleanSupplier held) {
        this.phrase = phrase;
        this.held = held;
        format = new AudioFormat(sampleRate, 16, 1, true, false);
    }

    @Override
    public float blockSeconds() {
        return 0.010F;
    }

    @Override
    public int queuedBlocks() {
        return 6;
    }

    VehicleHornPhrase phrase() {
        return phrase;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        if (closed || phrase.finished()) return null;
        int frames = size / 2;
        if (buffer == null || buffer.capacity() < frames * 2) {
            buffer = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
            samples = new float[frames];
        }
        int written = phrase.render(samples, frames, held.getAsBoolean());
        if (written == 0) return null;
        buffer.clear();
        for (int i = 0; i < written; i++) {
            buffer.putShort((short) Math.round(Math.clamp(samples[i], -1F, 1F) * 32767F));
        }
        buffer.flip();
        return buffer;
    }

    @Override
    public void close() {
        closed = true;
        buffer = null;
        samples = null;
    }
}
