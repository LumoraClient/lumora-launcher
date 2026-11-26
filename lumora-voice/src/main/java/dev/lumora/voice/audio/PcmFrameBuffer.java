package dev.lumora.voice.audio;

import dev.lumora.voice.protocol.VoiceCodec;
import java.util.ArrayDeque;

/** Sample FIFO: short Opus packets are joined and long packets are retained in full. */
public final class PcmFrameBuffer {
    private final ArrayDeque<short[]> chunks = new ArrayDeque<>();
    private int offset, samples;
    private long firstArrival;
    private boolean playing;
    public synchronized int available() { return samples; }
    public synchronized void offer(short[] pcm, long now) {
        if (pcm.length == 0) return;
        if (samples + pcm.length > VoiceCodec.SAMPLES * 16) clear();
        if (chunks.isEmpty() && !playing) firstArrival = now;
        chunks.addLast(pcm); samples += pcm.length;
    }
    /** Local monitoring waits for 40 ms of audio instead of racing independent input/output clocks. */
    public synchronized short[] monitor(long now) {
        if (!playing) {
            if (samples == 0 || samples < VoiceCodec.SAMPLES * 2 && now - firstArrival < 40_000_000L) return null;
            playing = true;
        }
        if (samples == 0) { playing = false; return null; }
        return read();
    }
    public synchronized short[] read() {
        short[] frame = new short[VoiceCodec.SAMPLES]; int written = 0;
        while (written < frame.length && !chunks.isEmpty()) {
            short[] chunk = chunks.getFirst(); int count = Math.min(frame.length - written, chunk.length - offset);
            System.arraycopy(chunk, offset, frame, written, count); offset += count; written += count; samples -= count;
            if (offset == chunk.length) { chunks.removeFirst(); offset = 0; }
        }
        return frame;
    }
    public synchronized void clear() { chunks.clear(); offset = samples = 0; playing = false; }
}
