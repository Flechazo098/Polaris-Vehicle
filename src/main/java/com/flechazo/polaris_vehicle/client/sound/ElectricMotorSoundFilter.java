package com.flechazo.polaris_vehicle.client.sound;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

public final class ElectricMotorSoundFilter implements AutoCloseable {
    private final int source, filter;
    private float lastGain = Float.NaN;
    private boolean closed;

    ElectricMotorSoundFilter(int source, int filter) {
        this.source = source;
        this.filter = filter;
    }

    public static ElectricMotorSoundFilter create(int source, float pitch) {
        if (!AL.getCapabilities().ALC_EXT_EFX) {
            return null;
        }
        int id = EXTEfx.alGenFilters();
        if (id == 0) return null;
        var result = new ElectricMotorSoundFilter(source, id);
        EXTEfx.alFilteri(id, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilterf(id, EXTEfx.AL_LOWPASS_GAIN, 1.0F);
        result.update(pitch);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            result.close();
            return null;
        }
        return result;
    }

    public static float highFrequencyGain(float pitch) {
        float progress = Math.clamp((pitch - 1.10F) / .58F, 0.0F, 1.0F);
        progress = progress * progress * (3.0F - 2.0F * progress);
        return .70F * (float) Math.pow(.25 / .70, progress);
    }

    public void update(float pitch) {
        if (closed) return;
        float gain = highFrequencyGain(pitch);
        if (Math.abs(gain - lastGain) < 1.0E-5F) return;
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gain);
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        lastGain = gain;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        EXTEfx.alDeleteFilters(filter);
    }
}
