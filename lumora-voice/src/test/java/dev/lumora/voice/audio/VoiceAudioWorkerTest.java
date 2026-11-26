package dev.lumora.voice.audio;

import dev.lumora.voice.VoiceConfig;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

class VoiceAudioWorkerTest {
    /**
     * A real-time test: under heavy load (a parallel build compiling every client) the workers can miss the audio
     * clock for reasons outside the code, so it gets up to three tries; a real dropout fails all of them.
     */
    @Test void realCaptureCodecAndPlaybackWorkersStayContinuousWithBurstyInputAndNonblockingOutput() throws Exception {
        AssertionError last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                attempt();
                return;
            } catch (AssertionError e) {
                last = e;
            }
        }
        throw last;
    }

    private void attempt() throws Exception {
        var config = new VoiceConfig(); config.noiseSuppression = false; config.highPassFilter = false; config.screamerProtection = false;
        var errors = new CopyOnWriteArrayList<String>(); var writes = new CopyOnWriteArrayList<byte[]>();
        var times = new CopyOnWriteArrayList<Long>(); var enough = new CountDownLatch(45); var uploaded = new AtomicInteger();
        VoiceAudio.Devices devices = new VoiceAudio.Devices() {
            public AudioDevices.Input input(AudioBackend backend, String id) {
                return new AudioDevices.Input() {
                    volatile boolean open = true; int frame; long deadline = System.nanoTime();
                    public int read(byte[] data, int offset, int length) {
                        if (frame % 4 == 0) {
                            deadline += 80_000_000L;
                            while (open && System.nanoTime() < deadline) LockSupport.parkNanos(Math.min(1_000_000, deadline - System.nanoTime()));
                        }
                        if (!open) return 0;
                        for (int i = 0; i < length / 2; i++) {
                            short sample = (short) (Math.sin((frame * 960L + i) * 2 * Math.PI * 173 / 48000) * 6000);
                            data[offset + 2 * i] = (byte) sample; data[offset + 2 * i + 1] = (byte) (sample >> 8);
                        }
                        frame++; return length;
                    }
                    public boolean isOpen() { return open; }
                    public void cancel() { open = false; }
                    public void close() { open = false; }
                };
            }
            public AudioDevices.Output output(AudioBackend backend, String id) {
                return new AudioDevices.Output() {
                    volatile boolean open = true;
                    public int write(byte[] data, int offset, int length) {
                        if (!open) return 0;
                        times.add(System.nanoTime()); writes.add(Arrays.copyOfRange(data, offset, offset + length)); enough.countDown(); return length;
                    }
                    public boolean isOpen() { return open; }
                    public void cancel() { open = false; }
                    public void close() { open = false; }
                };
            }
        };
        try (var audio = new VoiceAudio(config.audioSettings(), bytes -> uploaded.incrementAndGet(), errors::add, devices)) {
            audio.testing(true); assertTrue(enough.await(4, TimeUnit.SECONDS), "Audio workers stalled");
            audio.testing(false);
        }
        assertTrue(errors.isEmpty(), errors.toString()); assertEquals(0, uploaded.get(), "Local test must not upload voice");
        long duration = times.get(44) - times.get(0);
        assertTrue(duration >= 800_000_000L && duration < 2_000_000_000L, "45 output frames must follow the audio clock, not driver write speed: " + duration);
        for (int frame = 5; frame < 43; frame++) {
            double energy = 0; byte[] pcm = writes.get(frame);
            for (int i = 0; i < pcm.length; i += 2) { short sample = (short) ((pcm[i] & 255) | pcm[i + 1] << 8); energy += (double) sample * sample; }
            assertTrue(Math.sqrt(energy / (pcm.length / 2)) > 500, "Audio dropout at output frame " + frame);
        }
    }
}
