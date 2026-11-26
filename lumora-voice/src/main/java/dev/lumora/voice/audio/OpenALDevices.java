package dev.lumora.voice.audio;

import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC11.*;
import static org.lwjgl.openal.EXTThreadLocalContext.*;

/** Independent PCM backend using Minecraft's bundled LWJGL; never changes its process context. */
final class OpenALDevices {
    private OpenALDevices() {}
    static List<AudioDevices.Device> list(boolean input) {
        var devices = new ArrayList<AudioDevices.Device>();
        devices.add(new AudioDevices.Device("", "Systemstandard"));
        int specifier = input ? ALC_CAPTURE_DEVICE_SPECIFIER
                : alcIsExtensionPresent(0, "ALC_ENUMERATE_ALL_EXT") ? ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER;
        List<String> names = ALUtil.getStringList(0, specifier);
        if (names != null) for (String name : new LinkedHashSet<>(names)) devices.add(new AudioDevices.Device(name, name));
        return List.copyOf(devices);
    }
    private static void checkDevice(long device) {
        int error = alcGetError(device);
        if (error != ALC_NO_ERROR) throw new IllegalStateException("OpenAL-Gerätefehler " + error);
        if (alcIsExtensionPresent(device, "ALC_EXT_disconnect") && alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) == ALC_FALSE)
            throw new IllegalStateException("OpenAL-Audiogerät wurde getrennt");
    }
    private static void checkAudio() {
        int error = alGetError();
        if (error != AL_NO_ERROR) throw new IllegalStateException("OpenAL-Wiedergabefehler " + error);
    }
    private abstract static class Owned implements AudioDevices.Stream {
        final Thread owner = Thread.currentThread();
        volatile boolean cancelled;
        boolean closed;
        volatile long device;
        public final boolean isOpen() { return device != 0 && !cancelled; }
        public final void cancel() { cancelled = true; }
        final void owned() { if (Thread.currentThread() != owner) throw new IllegalStateException("OpenAL stream used from another thread"); }
        final void waitForSamples() {
            LockSupport.parkNanos(2_000_000);
            if (Thread.currentThread().isInterrupted()) cancelled = true;
        }
    }
    static final class Capture extends Owned implements AudioDevices.Input {
        private final ByteBuffer pcm = MemoryUtil.memAlloc(1920);
        Capture(String id) {
            try {
                device = alcCaptureOpenDevice(id.isEmpty() ? (String) null : id, 48000, AL_FORMAT_MONO16, 960 * 8);
                if (device == 0) throw new IllegalStateException("OpenAL-Mikrofon konnte nicht geöffnet werden");
                alcCaptureStart(device); checkDevice(device);
            } catch (RuntimeException | LinkageError failure) { close(); throw failure; }
        }
        public int read(byte[] bytes, int offset, int length) {
            owned(); int count = Math.min(length / 2, pcm.capacity() / 2);
            if (count == 0) return 0;
            while (isOpen() && alcGetInteger(device, ALC_CAPTURE_SAMPLES) < count) { checkDevice(device); waitForSamples(); }
            if (!isOpen()) return 0;
            pcm.clear(); alcCaptureSamples(device, pcm, count); checkDevice(device);
            // OpenAL uses native-endian PCM; the common pipeline uses little endian.
            for (int i = 0; i < count; i++) { short sample = pcm.getShort(i * 2); bytes[offset + i * 2] = (byte) sample; bytes[offset + i * 2 + 1] = (byte) (sample >> 8); }
            return count * 2;
        }
        public void close() {
            owned(); if (closed) return; closed = true; cancelled = true;
            if (device != 0) { alcCaptureStop(device); alcCaptureCloseDevice(device); device = 0; }
            MemoryUtil.memFree(pcm);
        }
    }
    static final class Playback extends Owned implements AudioDevices.Output {
        private long context;
        private int source;
        private final int[] buffers = new int[4];
        private final ArrayDeque<Integer> free = new ArrayDeque<>();
        private final ByteBuffer pcm = MemoryUtil.memAlloc(3840);
        private boolean bound;
        Playback(String id) {
            try {
                device = alcOpenDevice(id.isEmpty() ? (String) null : id);
                if (device == 0) throw new IllegalStateException("OpenAL-Ausgabe konnte nicht geöffnet werden");
                var capabilities = ALC.createCapabilities(device);
                if (!capabilities.ALC_EXT_thread_local_context) throw new IllegalStateException("OpenAL benötigt ALC_EXT_thread_local_context");
                context = alcCreateContext(device, (IntBuffer) null);
                if (context == 0 || !alcSetThreadContext(context)) throw new IllegalStateException("OpenAL-Kontext konnte nicht gestartet werden");
                bound = true; AL.createCapabilities(capabilities);
                source = alGenSources(); alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE); alSourcef(source, AL_ROLLOFF_FACTOR, 0);
                for (int i = 0; i < buffers.length; i++) { buffers[i] = alGenBuffers(); free.add(buffers[i]); }
                checkAudio();
            } catch (RuntimeException | LinkageError failure) { close(); throw failure; }
        }
        private void reclaim() {
            int processed = alGetSourcei(source, AL_BUFFERS_PROCESSED);
            while (processed-- > 0) free.add(alSourceUnqueueBuffers(source));
            checkAudio();
        }
        public int write(byte[] bytes, int offset, int length) {
            owned();
            if (length % 4 != 0) throw new IllegalArgumentException("Expected stereo PCM frames");
            int count = Math.min(length, pcm.capacity());
            while (isOpen()) {
                checkDevice(device);
                reclaim(); if (!free.isEmpty()) break; waitForSamples();
            }
            if (!isOpen()) return 0;
            pcm.clear();
            for (int i = 0; i < count; i += 2) pcm.putShort((short) ((bytes[offset + i] & 255) | (bytes[offset + i + 1] << 8)));
            pcm.flip(); int buffer = free.removeFirst();
            alBufferData(buffer, AL_FORMAT_STEREO16, pcm, 48000); alSourceQueueBuffers(source, buffer);
            if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) alSourcePlay(source);
            checkAudio(); return count;
        }
        public void close() {
            owned(); if (closed) return; closed = true; cancelled = true;
            if (bound) {
                if (source != 0) { alSourceStop(source); alDeleteSources(source); source = 0; }
                for (int i = 0; i < buffers.length; i++) if (buffers[i] != 0) { alDeleteBuffers(buffers[i]); buffers[i] = 0; }
                alcSetThreadContext(0); AL.setCurrentThread(null); bound = false;
            }
            if (context != 0) { alcDestroyContext(context); context = 0; }
            if (device != 0) { alcCloseDevice(device); device = 0; }
            MemoryUtil.memFree(pcm);
        }
    }
}
