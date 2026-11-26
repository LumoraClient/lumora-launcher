package dev.lumora.voice.protocol;

import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;

/** Control traffic is independent of the 20 ms voice clock. All times are monotonic nanoseconds. */
public final class VoiceSendQueue {
    public static final long FRAME_NANOS = 20_000_000L;
    private record Frame(byte[] bytes, long created) {}
    private final ArrayBlockingQueue<byte[]> controls = new ArrayBlockingQueue<>(128);
    private final ArrayDeque<Frame> audio = new ArrayDeque<>();
    private long nextAudio = Long.MIN_VALUE;
    public void control(byte[] bytes) { controls.offer(bytes); }
    public byte[] control() { return controls.poll(); }
    public synchronized void audio(byte[] bytes, long now) {
        if (audio.size() >= 8) audio.removeFirst();
        audio.addLast(new Frame(bytes, now));
    }
    public synchronized byte[] audio(long now) {
        while (!audio.isEmpty() && now - audio.getFirst().created() > 160_000_000L) audio.removeFirst();
        if (audio.isEmpty() || now < nextAudio) return null;
        nextAudio = nextAudio == Long.MIN_VALUE || now - nextAudio >= FRAME_NANOS
                ? now + FRAME_NANOS : nextAudio + FRAME_NANOS;
        return audio.removeFirst().bytes();
    }
    public synchronized void silence() { audio.clear(); nextAudio = Long.MIN_VALUE; }
    public void clear() { controls.clear(); silence(); }
}
