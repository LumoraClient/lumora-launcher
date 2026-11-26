package dev.lumora.voice.audio;

import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.protocol.VoiceCodec;
import io.github.jaredmdobson.concentus.OpusDecoder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class VoiceAudio implements AutoCloseable {
    interface Devices {
        AudioDevices.Input input(AudioBackend backend, String id) throws Exception;
        AudioDevices.Output output(AudioBackend backend, String id) throws Exception;
    }
    public record Spatial(float gain, float pan, boolean muffle, boolean echo) { public Spatial(float gain, float pan) { this(gain, pan, false, false); } }
    private record Received(UUID id, long sequence, byte[] opus, long arrival) {}
    private static final class Speaker {
        final OpusPlayout playout = new OpusPlayout();
        final EnvironmentalAudio effects = new EnvironmentalAudio();
        Speaker() throws Exception {}
    }
    private final ArrayBlockingQueue<Received> incoming = new ArrayBlockingQueue<>(512);
    private final PcmFrameBuffer monitor = new PcmFrameBuffer();
    private final ConcurrentMap<UUID, Long> speaking = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, float[]> waves = new ConcurrentHashMap<>();
    private final Consumer<byte[]> transmit;
    private final Consumer<String> error;
    private final Devices devices;
    private final AtomicLong captureGeneration = new AtomicLong();
    private volatile VoiceConfig.AudioSettings settings;
    private volatile boolean running = true, requested, test, deafened, talking, captureFailed;
    private volatile float levelDb = -100, autoThresholdDb = -40;
    private volatile AudioDevices.Input microphone;
    private volatile AudioDevices.Output output;
    private volatile Map<UUID, Spatial> positions = Map.of();
    public VoiceAudio(VoiceConfig.AudioSettings settings, Consumer<byte[]> transmit, Consumer<String> error) {
        this(settings, transmit, error, new Devices() {
            public AudioDevices.Input input(AudioBackend backend, String id) throws Exception { return AudioDevices.input(backend, id); }
            public AudioDevices.Output output(AudioBackend backend, String id) throws Exception { return AudioDevices.output(backend, id); }
        });
    }
    VoiceAudio(VoiceConfig.AudioSettings settings, Consumer<byte[]> transmit, Consumer<String> error, Devices devices) {
        this.settings = settings; this.transmit = transmit; this.error = error; this.devices = devices;
        daemon("LumoraVoice Capture", this::capture); daemon("LumoraVoice Playback", this::playback);
    }
    private static void daemon(String name, Runnable task) { var thread = new Thread(task, name); thread.setDaemon(true); thread.start(); }
    public void configure(VoiceConfig.AudioSettings value) {
        VoiceConfig.AudioSettings previous = settings; settings = value;
        if (!previous.inputDevice().equals(value.inputDevice()) || previous.inputBackend() != value.inputBackend()) { captureFailed = false; closeMic(); }
        if (!previous.outputDevice().equals(value.outputDevice()) || previous.outputBackend() != value.outputBackend()) { var line = output; if (line != null) line.cancel(); }
    }
    public void positions(Map<UUID, Spatial> value) { positions = Map.copyOf(value); }
    public void gate(boolean requested, boolean deafened) {
        this.requested = requested; this.deafened = deafened;
        if (!requested && !test) { talking = false; levelDb = -100; closeMic(); }
        if (deafened) incoming.clear();
    }
    public void testing(boolean value) {
        test = value; talking = false; captureFailed = false; monitor.clear();
        if (!value && !requested) closeMic();
    }
    public void retryDevices() { captureFailed = false; closeMic(); var line = output; if (line != null) line.cancel(); }
    public boolean testing() { return test; }
    public boolean talking() { return talking; }
    public boolean microphoneOpen() { var line = microphone; return line != null && line.isOpen(); }
    public float levelDb() { return levelDb; }
    /** Current automatic voice threshold (follows the background noise while the microphone is open). */
    public float autoThresholdDb() { return autoThresholdDb; }
    /** The threshold voice activation uses now: the automatic one or the manual setting. */
    public float thresholdDb() { var config = settings; return config.autoThreshold() ? autoThresholdDb : config.thresholdDb(); }
    public boolean speaking(UUID id) { return System.currentTimeMillis() - speaking.getOrDefault(id, 0L) < 300; }
    public float[] wave(UUID id) { float[] wave = waves.get(id); return speaking(id) && wave != null ? wave.clone() : new float[7]; }
    public void receive(UUID id, long sequence, byte[] opus) { if (running && !deafened && positions.containsKey(id)) incoming.offer(new Received(id, sequence, opus, System.nanoTime())); }
    private void closeMic() { captureGeneration.incrementAndGet(); var line = microphone; if (line != null) line.cancel(); }
    private void capture() {
        NoiseSuppression denoiser = null;
        try {
            var encoder = VoiceCodec.encoder(); var monitorEncoder = VoiceCodec.encoder();
            var monitorDecoder = new OpusDecoder(48000, 1); var processing = new AudioProcessing();
            while (running) {
                if ((!requested && !test) || captureFailed) { sleep(15); continue; }
                long generation = captureGeneration.get();
                var selection = settings;
                String device = selection.inputDevice();
                AudioBackend backend = selection.inputBackend();
                try (var line = devices.input(backend, device)) {
                    microphone = line;
                    if (!running || (!requested && !test) || generation != captureGeneration.get()) continue;
                    encoder.resetState(); monitorEncoder.resetState(); monitorDecoder.resetState(); processing.reset();
                    byte[] bytes = new byte[VoiceCodec.SAMPLES * 2];
                    while (running && (requested || test) && line.isOpen() && generation == captureGeneration.get()) {
                        int read = 0;
                        while (read < bytes.length && running && (requested || test) && line.isOpen() && generation == captureGeneration.get()) {
                            int count = line.read(bytes, read, bytes.length - read); if (count <= 0) break; read += count;
                        }
                        if (read != bytes.length || !running || (!requested && !test) || generation != captureGeneration.get()) break;
                        short[] raw = new short[VoiceCodec.SAMPLES];
                        for (int i = 0; i < raw.length; i++) raw[i] = (short) ((bytes[2 * i] & 255) | (bytes[2 * i + 1] << 8));
                        var config = settings;
                        if (config.noiseSuppression() && denoiser == null) {
                            try { denoiser = new NoiseSuppression(); }
                            catch (LinkageError | RuntimeException unavailable) { error.accept("RNNoise konnte nicht geladen werden. Rauschunterdrückung deaktivieren und Geräte neu öffnen."); captureFailed = true; break; }
                        } else if (!config.noiseSuppression() && denoiser != null) { denoiser.close(); denoiser = null; }
                        short[] pcm = processing.process(denoiser == null ? raw : denoiser.process(raw), config); levelDb = AudioProcessing.db(pcm);
                        autoThresholdDb = processing.autoThreshold(levelDb);
                        float threshold = config.autoThreshold() ? autoThresholdDb : config.thresholdDb();
                        boolean active = config.activation() != VoiceConfig.Activation.VOICE || processing.voiceActive(levelDb, threshold, config.releaseMillis());
                        talking = requested && !test && active;
                        if (test) monitor.offer(VoiceCodec.decode(monitorDecoder, VoiceCodec.encode(monitorEncoder, pcm)), System.nanoTime());
                        else if (talking) transmit.accept(VoiceCodec.encode(encoder, pcm));
                    }
                } catch (Exception | LinkageError failure) {
                    if (running && (requested || test) && generation == captureGeneration.get()) {
                        captureFailed = true; error.accept(backend.label() + ": Mikrofon nicht verfügbar. Geräteauswahl und Mikrofonzugriff prüfen, dann Geräte neu öffnen.");
                    }
                } finally { microphone = null; talking = false; levelDb = -100; }
            }
        } catch (Exception failure) { error.accept("Der Opus-Encoder konnte nicht gestartet werden."); }
        finally { if (denoiser != null) denoiser.close(); }
    }
    private void playback() {
        Map<UUID, Speaker> speakers = new HashMap<>(); String device = ""; AudioBackend backend = AudioBackend.JAVAX;
        var pacer = new FramePacer();
        while (running) {
            pacer.awaitNext();
            try {
                var config = settings;
                if (output != null && (!output.isOpen() || !device.equals(config.outputDevice()) || backend != config.outputBackend())) { output.close(); output = null; }
                if (deafened) { speakers.clear(); speaking.clear(); waves.clear(); }
                Received packet; int processed = 0;
                while (processed++ < 512 && (packet = incoming.poll()) != null) {
                    if (deafened || !positions.containsKey(packet.id())) continue;
                    Speaker speaker = speakers.get(packet.id());
                    if (speaker == null) { if (speakers.size() >= 64) continue; speaker = new Speaker(); speakers.put(packet.id(), speaker); }
                    speaker.playout.offer(packet.sequence(), packet.opus(), packet.arrival());
                    speaking.put(packet.id(), System.currentTimeMillis());
                }
                long now = System.currentTimeMillis();
                long audioNow = System.nanoTime();
                speakers.entrySet().removeIf(e -> audioNow - e.getValue().playout.lastArrival() > 15_000_000_000L || !positions.containsKey(e.getKey()));
                speaking.entrySet().removeIf(e -> now - e.getValue() > 15000);
                waves.keySet().removeIf(id -> !speaking.containsKey(id));
                float[] mix = new float[VoiceCodec.SAMPLES * 2]; boolean active = false;
                for (var entry : speakers.entrySet()) {
                    short[] pcm;
                    try { pcm = entry.getValue().playout.poll(audioNow); } catch (Exception malformed) { continue; }
                    Spatial spatial = positions.get(entry.getKey());
                    if (pcm == null || spatial == null || deafened) continue;
                    float[] wave = new float[7];
                    for (int i = 0; i < pcm.length; i++) { int column = Math.min(6, i * 7 / pcm.length); wave[column] = Math.max(wave[column], Math.abs((int) pcm[i]) / 32768f); }
                    waves.put(entry.getKey(), wave);
                    pcm = entry.getValue().effects.process(pcm, spatial.muffle(), spatial.echo(), config.volumeCompressor());
                    VoiceCodec.mix(pcm, spatial.gain(), spatial.pan(), mix); active = true;
                }
                short[] own = test ? monitor.monitor(audioNow) : null;
                if (own != null) { VoiceCodec.mix(own, 1, 0, mix); active = true; }
                for (int i = 0; i < mix.length; i++) mix[i] *= config.outputVolume();
                if (config.protection()) AudioProcessing.protect(mix, config.protectionLevel(), config.maxVolume());
                if (active && output == null) {
                    device = config.outputDevice(); backend = config.outputBackend();
                    output = devices.output(backend, device);
                }
                var line = output;
                if (line != null) {
                    byte[] bytes = VoiceCodec.output(mix); int written = 0;
                    while (running && line.isOpen() && written < bytes.length) { int count = line.write(bytes, written, bytes.length - written); if (count <= 0) break; written += count; }
                }
            } catch (Exception | LinkageError failure) {
                var line = output; output = null; if (line != null) line.close();
                if (running) { error.accept(settings.outputBackend().label() + ": Wiedergabegerät nicht verfügbar. Ausgabe auswählen oder Geräte neu öffnen."); sleep(1000); }
            }
        }
        var line = output; if (line != null) line.close(); output = null;
    }
    private static void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
    @Override public void close() {
        running = requested = test = talking = false; closeMic(); var line = output; if (line != null) line.cancel();
        incoming.clear(); monitor.clear(); speaking.clear(); waves.clear(); positions = Map.of();
    }
}
