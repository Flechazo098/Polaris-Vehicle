package com.flechazo.polaris_vehicle.client.sound;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VehicleEnginePcmStream implements AudioStream, VehicleShortBlockPcmStream {
    private final EnginePcmMixer mixer;
    private final VehicleEngineSoundMix controls;
    private final AudioFormat format;
    private ByteBuffer buffer;
    private short[] samples;
    private boolean closed;

    VehicleEnginePcmStream(VehicleEnginePcmBank.Bank bank, VehicleEngineSoundMix controls) {
        mixer = new EnginePcmMixer(bank.loops(), bank.correlation(), bank.sampleRate(), bank.baseHz(), bank.calibration());
        this.controls = controls;
        format = new AudioFormat(bank.sampleRate(), 16, 1, true, false);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        if (closed) return null;
        int frames = size / 2;
        if (buffer == null || buffer.capacity() < frames * 2) {
            buffer = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN);
            samples = new short[frames];
        }
        mixer.render(samples, frames, controls.pcmControl());
        buffer.clear();
        buffer.asShortBuffer().put(samples, 0, frames);
        buffer.limit(frames * 2);
        return buffer;
    }

    @Override
    public void close() {
        closed = true;
        buffer = null;
        samples = null;
    }
}
