package dev.lumora.voice.audio;

import dev.lumora.voice.protocol.VoiceCodec;
import io.github.jaredmdobson.concentus.OpusDecoder;
import java.util.TreeMap;

/** Reorders encoded packets before decoding, then consumes exactly 960 samples per output tick. */
public final class OpusPlayout {
    private final OpusDecoder decoder = new OpusDecoder(48000, 1);
    private final TreeMap<Long, byte[]> packets = new TreeMap<>();
    private final PcmFrameBuffer pcm = new PcmFrameBuffer();
    private long expected = -1, firstArrival, lastArrival;
    private int lastSamples = VoiceCodec.SAMPLES;
    private boolean playing;
    public OpusPlayout() throws Exception {}
    public void offer(long sequence, byte[] opus, long now) {
        if (sequence == 0 && expected > 0 && now - lastArrival > 200_000_000L) {
            packets.clear(); pcm.clear(); decoder.resetState(); playing = false; expected = -1;
        }
        if (sequence < 0 || opus.length == 0 || opus.length > 1024 || sequence < expected || packets.containsKey(sequence)) return;
        if (packets.size() >= 32) { packets.clear(); pcm.clear(); playing = false; }
        if (!playing && packets.isEmpty()) firstArrival = now;
        packets.put(sequence, opus); lastArrival = now;
    }
    public short[] poll(long now) throws Exception {
        if (!playing) {
            if (packets.isEmpty() || now - firstArrival < 40_000_000L) return null;
            expected = packets.firstKey(); playing = true;
        }
        while (pcm.available() < VoiceCodec.SAMPLES) {
            byte[] packet = packets.remove(expected);
            if (packet != null) {
                try {
                    short[] decoded = VoiceCodec.decode(decoder, packet); lastSamples = decoded.length;
                    pcm.offer(decoded, now);
                } catch (Exception malformed) {
                    pcm.offer(VoiceCodec.conceal(decoder, lastSamples), now);
                }
                expected++;
            } else if (!packets.isEmpty() && packets.firstKey() - expected > 4) {
                // A long interruption starts a new talk spurt, rather than seconds of synthetic audio.
                decoder.resetState(); expected = packets.firstKey();
            } else if (packets.isEmpty()) {
                playing = false;
                return pcm.available() == 0 ? null : pcm.read();
            } else {
                // Conceal only confirmed sequence gaps; a normal pause must not advance
                // the decoder or discard sequence numbers from the next talk spurt.
                pcm.offer(VoiceCodec.conceal(decoder, lastSamples), now); expected++;
            }
        }
        return pcm.read();
    }
    public long lastArrival() { return lastArrival; }
}
