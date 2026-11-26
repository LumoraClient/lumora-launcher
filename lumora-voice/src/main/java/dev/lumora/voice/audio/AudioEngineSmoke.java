package dev.lumora.voice.audio;

import org.lwjgl.openal.AL;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.protocol.VoiceCodec;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.openal.ALC10.alcGetCurrentContext;
import static org.lwjgl.openal.EXTThreadLocalContext.alcGetThreadContext;

/** Opt-in integration check: enumeration and silent output, never microphone capture. */
public final class AudioEngineSmoke {
    private AudioEngineSmoke() {}
    public static void verify() throws Exception {
        if (!Boolean.getBoolean("lumoravoice.smoke")) throw new IllegalStateException("Smoke mode required");
        long initialContext = alcGetCurrentContext(), initialThreadContext = alcGetThreadContext();
        var initialCapabilities = AL.getCapabilities();
        for (var backend : AudioBackend.values()) {
            if (AudioDevices.list(backend, true).isEmpty() || AudioDevices.list(backend, false).isEmpty()) throw new IllegalStateException("Missing default audio device");
            for (int cycle = 0; cycle < 3; cycle++) {
                try (var output = AudioDevices.output(backend, "")) {
                    byte[] silence = new byte[3840];
                    for (int frame = 0; frame < 12; frame++) if (output.write(silence, 0, silence.length) != silence.length) throw new IllegalStateException("Incomplete stereo PCM frame");
                    var cancellation = new Thread(output::cancel, "LumoraVoice Smoke Cancel"); cancellation.start(); cancellation.join();
                    if (output.isOpen() || output.write(silence, 0, silence.length) != 0) throw new IllegalStateException("Cancelled audio stream remained active");
                }
                if (alcGetCurrentContext() != initialContext || alcGetThreadContext() != initialThreadContext || AL.getCapabilities() != initialCapabilities)
                    throw new IllegalStateException("Audio engine changed Minecraft's sound context");
            }
            verifyVoiceWorkers(backend);
        }
    }
    private static void verifyVoiceWorkers(AudioBackend backend) throws Exception {
        var config = new VoiceConfig(); config.outputBackend = backend;
        var errors = new CopyOnWriteArrayList<String>(); var written = new CountDownLatch(45); var closed = new CountDownLatch(1);
        var first = new AtomicLong(); var last = new AtomicLong();
        var devices = new VoiceAudio.Devices() {
            public AudioDevices.Input input(AudioBackend ignored, String id) { throw new AssertionError("Smoke must never open a microphone"); }
            public AudioDevices.Output output(AudioBackend selected, String id) throws Exception {
                var actual = AudioDevices.output(selected, id);
                return new AudioDevices.Output() {
                    public int write(byte[] pcm, int offset, int length) {
                        int count = actual.write(pcm, offset, length); long now = System.nanoTime();
                        if (count > 0 && written.getCount() > 0) { first.compareAndSet(0, now); last.set(now); written.countDown(); }
                        return count;
                    }
                    public boolean isOpen() { return actual.isOpen(); }
                    public void cancel() { actual.cancel(); }
                    public void close() { try { actual.close(); } finally { closed.countDown(); } }
                };
            }
        };
        UUID sender = new UUID(0, 12345);
        try (var voice = new VoiceAudio(config.audioSettings(), bytes -> { throw new AssertionError("Unexpected upload"); }, errors::add, devices)) {
            voice.positions(Map.of(sender, new VoiceAudio.Spatial(1, 0)));
            var encoder = VoiceCodec.encoder();
            for (int burst = 0; burst < 10; burst++) {
                for (int frame = 0; frame < 5; frame++) voice.receive(sender, burst * 5L + frame, VoiceCodec.encode(encoder, new short[960]));
                Thread.sleep(100);
            }
            if (!written.await(2, TimeUnit.SECONDS) || !errors.isEmpty()) throw new IllegalStateException("Native voice workers failed: " + errors);
            if (last.get() - first.get() < 800_000_000L) throw new IllegalStateException("Native voice output bypassed the 20 ms clock");
        }
        if (!closed.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Native voice worker did not release its device");
    }
}
