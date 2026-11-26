package dev.lumora.voice.audio;

import javax.sound.sampled.*;
import java.util.*;

public final class AudioDevices {
    public interface Stream extends AutoCloseable {
        boolean isOpen();
        /** May be called from the game thread; native destruction stays on the owning worker. */
        void cancel();
        @Override void close();
    }
    public interface Input extends Stream { int read(byte[] bytes, int offset, int length); }
    public interface Output extends Stream { int write(byte[] bytes, int offset, int length); }
    public record Device(String id, String name) {}
    public static final AudioFormat INPUT = new AudioFormat(48000, 16, 1, true, false);
    public static final AudioFormat OUTPUT = new AudioFormat(48000, 16, 2, true, false);
    private AudioDevices() {}
    private static String id(Mixer.Info info) { return info.getName() + " | " + info.getVendor() + " | " + info.getVersion(); }
    public static List<Device> list(AudioBackend backend, boolean input) {
        if (!backend.available()) throw new IllegalStateException(backend.label() + " ist in dieser Minecraft-Version nicht verfügbar.");
        if (backend == AudioBackend.OPENAL) return OpenALDevices.list(input);
        List<Device> devices = new ArrayList<>();
        devices.add(new Device("", "Systemstandard"));
        Line.Info type = new DataLine.Info(input ? TargetDataLine.class : SourceDataLine.class, input ? INPUT : OUTPUT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try { if (AudioSystem.getMixer(info).isLineSupported(type)) devices.add(new Device(id(info), info.getName())); }
            catch (IllegalArgumentException ignored) {}
        }
        return List.copyOf(devices);
    }
    private static Mixer selected(String id) throws LineUnavailableException {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) if (id(info).equals(id)) return AudioSystem.getMixer(info);
        throw new LineUnavailableException("Gewähltes Audiogerät ist nicht mehr verfügbar. Bitte neu auswählen.");
    }
    public static Input input(AudioBackend backend, String id) throws LineUnavailableException {
        if (!backend.available()) throw new LineUnavailableException(backend.label() + " ist in dieser Minecraft-Version nicht verfügbar.");
        if (backend == AudioBackend.OPENAL) return new OpenALDevices.Capture(id);
        TargetDataLine line = id.isEmpty() ? AudioSystem.getTargetDataLine(INPUT) : (TargetDataLine) selected(id).getLine(new DataLine.Info(TargetDataLine.class, INPUT));
        try { line.open(INPUT, 960 * 8); line.start(); }
        catch (RuntimeException | LineUnavailableException failure) { line.close(); throw failure; }
        return new Input() {
            public int read(byte[] bytes, int offset, int length) { return line.isOpen() ? line.read(bytes, offset, length) : 0; }
            public boolean isOpen() { return line.isOpen(); }
            public void cancel() { line.close(); }
            public void close() { line.close(); }
        };
    }
    public static Output output(AudioBackend backend, String id) throws LineUnavailableException {
        if (!backend.available()) throw new LineUnavailableException(backend.label() + " ist in dieser Minecraft-Version nicht verfügbar.");
        if (backend == AudioBackend.OPENAL) return new OpenALDevices.Playback(id);
        SourceDataLine line = id.isEmpty() ? AudioSystem.getSourceDataLine(OUTPUT) : (SourceDataLine) selected(id).getLine(new DataLine.Info(SourceDataLine.class, OUTPUT));
        try { line.open(OUTPUT, 960 * 16); line.start(); }
        catch (RuntimeException | LineUnavailableException failure) { line.close(); throw failure; }
        return new Output() {
            public int write(byte[] bytes, int offset, int length) { return line.isOpen() ? line.write(bytes, offset, length) : 0; }
            public boolean isOpen() { return line.isOpen(); }
            public void cancel() { line.close(); }
            public void close() { line.close(); }
        };
    }
}
