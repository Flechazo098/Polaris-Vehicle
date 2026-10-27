package com.flechazo.polaris_vehicle.client.sound;

/**
 * A horn played the way a real one sounds: attack, sustain for as long as the
 * button is held, then its own release.
 *
 * <p>The three parts are cut from one recording: {@code start} ends on the
 * sample immediately before {@code loop}, and {@code tail} is the recording's
 * natural ending. On release the sustain does not fade. Within one waveform
 * period the sustain is joined to the tail at the point where the two waveforms
 * agree best, with a few-millisecond blend only to keep that join click-free,
 * and the stream ends with the tail's own silence.</p>
 */
public final class VehicleHornPhrase {
    /**
     * Longest wait for a matching join: about one beat of the two horn tones.
     */
    static final double MAX_JOIN_WAIT_SECONDS = 0.025;
    static final double JOIN_WINDOW_SECONDS = 0.010;
    static final double JOIN_BLEND_SECONDS = 0.005;

    private enum Stage {START, SUSTAIN, JOIN, TAIL, FINISHED}

    private final float[] start;
    private final float[] loop;
    private final float[] tail;
    private final int maxWait;
    private final int window;
    private final int blend;
    private Stage stage = Stage.START;
    private int position;
    private int joinAt = -1;
    private int joinOffset;
    private volatile boolean releasing;

    public VehicleHornPhrase(float[] start, float[] loop, float[] tail, int sampleRate) {
        if (start.length == 0 || loop.length == 0 || tail.length == 0) {
            throw new IllegalArgumentException("Horn phrase parts must not be empty");
        }
        this.start = start;
        this.loop = loop;
        this.tail = tail;
        maxWait = Math.max(1, (int) Math.round(sampleRate * MAX_JOIN_WAIT_SECONDS));
        window = Math.clamp((int) Math.round(sampleRate * JOIN_WINDOW_SECONDS), 1, tail.length);
        blend = Math.clamp((int) Math.round(sampleRate * JOIN_BLEND_SECONDS), 1, tail.length);
    }

    public boolean releasing() {
        return releasing;
    }

    public boolean finished() {
        return stage == Stage.FINISHED;
    }

    /**
     * Writes up to {@code frames} samples.
     *
     * @param held whether the horn button is still held
     * @return the number of samples written; fewer than requested only at the end
     */
    public int render(float[] out, int frames, boolean held) {
        int written = 0;
        while (written < frames && stage != Stage.FINISHED) {
            switch (stage) {
                case START -> {
                    out[written++] = start[position++];
                    if (position == start.length) {
                        stage = Stage.SUSTAIN;
                        position = 0;
                    }
                }
                case SUSTAIN -> {
                    // A tap still plays the complete attack; release is only
                    // considered once the sustain has been reached.
                    if (!held) {
                        joinAt = position + bestJoinDelay(position);
                        stage = Stage.JOIN;
                        releasing = true;
                        continue;
                    }
                    out[written++] = loop[position % loop.length];
                    position = (position + 1) % loop.length;
                }
                case JOIN -> {
                    if (position < joinAt) {
                        out[written++] = loop[position % loop.length];
                        position++;
                    } else if (joinOffset < blend) {
                        float weight = (joinOffset + 0.5F) / blend;
                        out[written++] = loop[(joinAt + joinOffset) % loop.length] * (1 - weight)
                                + tail[joinOffset] * weight;
                        joinOffset++;
                    } else {
                        stage = Stage.TAIL;
                        position = blend;
                    }
                }
                case TAIL -> {
                    out[written++] = tail[position++];
                    if (position == tail.length) stage = Stage.FINISHED;
                }
                default -> throw new IllegalStateException();
            }
        }
        if (stage == Stage.FINISHED) releasing = true;
        return written;
    }

    /**
     * Delay (samples) at which the upcoming sustain best matches the start of the tail.
     */
    int bestJoinDelay(int from) {
        int bestDelay = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        double tailEnergy = 0;
        for (int k = 0; k < window; k++) tailEnergy += tail[k] * tail[k];
        for (int delay = 0; delay < maxWait; delay++) {
            double dot = 0, energy = 0;
            for (int k = 0; k < window; k++) {
                float sample = loop[(from + delay + k) % loop.length];
                dot += sample * tail[k];
                energy += sample * sample;
            }
            double score = dot / Math.sqrt(Math.max(1E-12, energy * tailEnergy));
            if (score > bestScore) {
                bestScore = score;
                bestDelay = delay;
            }
        }
        return bestDelay;
    }
}
