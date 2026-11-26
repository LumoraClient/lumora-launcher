package dev.lumora.voice.audio;

import java.util.concurrent.locks.LockSupport;
import dev.lumora.voice.protocol.VoiceSendQueue;

/** One audio frame per 20 ms, independent of whether an audio driver blocks in write(). */
final class FramePacer {
    private long next = System.nanoTime();
    void awaitNext() {
        long now = System.nanoTime();
        while (now < next && !Thread.currentThread().isInterrupted()) { LockSupport.parkNanos(next - now); now = System.nanoTime(); }
        next = now - next >= VoiceSendQueue.FRAME_NANOS ? now + VoiceSendQueue.FRAME_NANOS : next + VoiceSendQueue.FRAME_NANOS;
    }
}
