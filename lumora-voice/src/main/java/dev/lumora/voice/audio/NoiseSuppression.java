package dev.lumora.voice.audio;

import cn.enaium.rnnoise.Rnnoise;
import cn.enaium.rnnoise.Rnnoise_jvmKt;

/** Bundled RNNoise; model and processing remain on this computer. */
public final class NoiseSuppression implements AutoCloseable {
    private final Rnnoise filter = Rnnoise_jvmKt.createRnnoise(null);
    private final float[] input = new float[480], output = new float[480];
    public short[] process(short[] pcm) {
        if (pcm.length % 480 != 0) throw new IllegalArgumentException("RNNoise requires 10 ms frames");
        short[] result = new short[pcm.length];
        for (int offset = 0; offset < pcm.length; offset += 480) {
            for (int i = 0; i < 480; i++) input[i] = pcm[offset + i] / 32768f;
            filter.processFrame(input, output);
            for (int i = 0; i < 480; i++) result[offset + i] = (short) Math.clamp(Math.round(output[i] * 32768), -32768, 32767);
        }
        return result;
    }
    @Override public void close() { try { filter.close(); } catch (Exception ignored) {} }
}
