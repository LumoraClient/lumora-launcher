package dev.lumora.voice.audio;

public final class EnvironmentalAudio {
    private final float[] delay = new float[9600];
    private int cursor;
    private float lowPass, compressorGain = 1;
    public short[] process(short[] input, boolean muffle, boolean echo, boolean compress) {
        float db = AudioProcessing.db(input);
        float target = compress && db > -55 ? Math.clamp((float) Math.pow(10, (-22 - db) / 20), .2f, 3) : 1;
        compressorGain += (target - compressorGain) * (target < compressorGain ? .65f : .04f);
        short[] result = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            float sample = input[i] * compressorGain;
            lowPass += .11f * (sample - lowPass);
            if (muffle) sample = lowPass * .72f;
            float reflection = delay[cursor];
            delay[cursor] = echo ? sample * .25f + reflection * .3f : 0;
            cursor = (cursor + 1) % delay.length;
            result[i] = (short) Math.clamp(Math.round(sample + (echo ? reflection : 0)), -32768, 32767);
        }
        return result;
    }
}
