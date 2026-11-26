package dev.lumora.voice.audio;

import dev.lumora.voice.VoiceConfig;

/** Local DSP: DC/high-pass filter, gate, bounded automatic gain and peak protection. */
public final class AudioProcessing {
    private float previousInput, previousOutput, automaticGain = 1;
    private int voiceHold;
    public static float db(short[] pcm) {
        double squares = 0;
        for (short sample : pcm) squares += (double) sample * sample;
        return (float) (20 * Math.log10(Math.max(1e-5, Math.sqrt(squares / Math.max(1, pcm.length)) / 32768)));
    }
    public short[] process(short[] input, VoiceConfig.AudioSettings config) {
        short[] out = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            float sample = input[i];
            float filtered = .987f * (previousOutput + sample - previousInput); // ~100 Hz at 48 kHz
            previousInput = sample; previousOutput = filtered;
            out[i] = clip(config.highPass() ? filtered : sample);
        }
        float db = db(out);
        if (config.noiseGate() && db < config.gateThresholdDb()) return new short[input.length];
        if (config.automaticGain()) {
            float target = db < -55 ? 1 : Math.clamp((float) Math.pow(10, (-22 - db) / 20), .25f, 3);
            automaticGain += (target - automaticGain) * (target < automaticGain ? .3f : .015f);
        } else automaticGain = 1;
        // automatic gain replaces the manual gain (the slider is greyed out meanwhile)
        float gain = config.automaticGain() ? automaticGain : config.microphoneVolume();
        for (int i = 0; i < out.length; i++) out[i] = clip(out[i] * gain);
        return out;
    }
    public boolean voiceActive(float db, float threshold, int releaseMillis) {
        if (db >= threshold) voiceHold = (releaseMillis + 19) / 20;
        else if (voiceHold > 0) { voiceHold--; return true; }
        return db >= threshold;
    }
    /** Distance of the automatic voice threshold above the measured background noise. */
    public static final float AUTO_MARGIN_DB = 10;
    public static final float AUTO_MIN_DB = -60, AUTO_MAX_DB = -20;
    private float noiseFloor = Float.NaN;

    /**
     * Automatic voice threshold: follows the background noise of the microphone (falls quickly, rises slowly near the
     * noise and hardly at all while speaking), plus {@link #AUTO_MARGIN_DB}. Called once per 20 ms frame.
     */
    public float autoThreshold(float db) {
        if (db > -99) {
            if (Float.isNaN(noiseFloor)) noiseFloor = db;
            else if (db < noiseFloor) noiseFloor += (db - noiseFloor) * .25f;
            else if (db - noiseFloor < 10) noiseFloor += (db - noiseFloor) * .01f;
            else noiseFloor += .002f;
        }
        return Float.isNaN(noiseFloor) ? -40 : Math.clamp(noiseFloor + AUTO_MARGIN_DB, AUTO_MIN_DB, AUTO_MAX_DB);
    }

    public void reset() { previousInput = previousOutput = 0; automaticGain = 1; voiceHold = 0; noiseFloor = Float.NaN; }
    public static void protect(float[] pcm, float thresholdPercent, float maxPercent) {
        float peak = 0;
        for (float sample : pcm) peak = Math.max(peak, Math.abs(sample));
        if (peak <= thresholdPercent * 327.67f) return;
        float gain = Math.min(1, maxPercent * 327.67f / peak);
        for (int i = 0; i < pcm.length; i++) pcm[i] *= gain;
    }
    private static short clip(float sample) { return (short) Math.clamp(Math.round(sample), -32768, 32767); }
}
