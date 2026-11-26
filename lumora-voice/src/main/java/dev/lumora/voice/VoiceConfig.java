package dev.lumora.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.lumora.voice.audio.AudioBackend;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Contains preferences only, never account credentials or microphone recordings. */
public final class VoiceConfig {
    public enum Activation { PUSH_TO_TALK, VOICE, CONTINUOUS }
    public enum Corner { BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT }
    /** Sprachdienst: AUTO nimmt Simple Voice Chat oder Plasmo Voice, wenn der Server den Kanal anbietet, sonst Laby Voice. */
    public enum Service {
        AUTO("Automatisch"), LABY("Laby Voice"), SIMPLE_VOICE_CHAT("Simple Voice Chat"), PLASMO_VOICE("Plasmo Voice");
        private final String label;
        Service(String label) { this.label = label; }
        public String label() { return label; }
    }
    /** 3D-Audio: Aus (beide Ohren gleich), Normal (links/rechts nach Blickrichtung), Stark (deutlichere Seiten, hinten leiser). */
    public enum SpatialMode {
        OFF("Aus"), NORMAL("Normal"), STRONG("Stark");
        private final String label;
        SpatialMode(String label) { this.label = label; }
        public String label() { return label; }
        /** Stereo-Position aus dem seitlichen Anteil der Richtung (-1 links … 1 rechts). */
        public float pan(float side) {
            float s = Math.clamp(side, -1, 1);
            return switch (this) { case OFF -> 0; case NORMAL -> s; case STRONG -> Math.signum(s) * (float) Math.sqrt(Math.abs(s)); };
        }
        /** Lautstärkefaktor aus dem Anteil nach vorne (-1 hinten … 1 vorne): bei Stark sind Stimmen hinter dir leiser. */
        public float rearGain(float front) { return this == STRONG && front < 0 ? 1 + .3f * Math.max(-1, front) : 1; }
    }
    public Service service = Service.AUTO;
    public boolean enabled, muted, outputMuted, deafened, togglePtt, speakInMenus;
    public boolean pauseUnfocused = true;
    public Activation activation = Activation.PUSH_TO_TALK;
    public String inputDevice = "", outputDevice = "";
    public boolean alternativeAudioInterfaces;
    public AudioBackend inputBackend = AudioBackend.JAVAX, outputBackend = AudioBackend.JAVAX;
    public float microphoneVolume = 1, outputVolume = 1, activationThresholdDb = -40;
    /** Sprachschwelle automatisch aus dem Grundrauschen bestimmen statt {@link #activationThresholdDb}. */
    public boolean autoThreshold;
    public int releaseMillis = 300;
    public boolean highPassFilter = true, noiseGate, automaticGain;
    public boolean noiseSuppression = true, volumeCompressor = true;
    public boolean ambientSound, ambientEcho = true, ambientMuffle = true;
    public boolean useChannelActivation;
    public Activation channelActivation = Activation.VOICE;
    public float focusedVolume = 1.5f, unfocusedVolume = .5f;
    public boolean backupServer, testServer;
    public boolean showTabIcons = true, showChannelNames = true, speechBubbleWave, showAudioWaves = true, coloredNames, hideInactive = true, showIndicatorText = true;
    public float gateThresholdDb = -55;
    public float surroundRange = 18;
    public boolean spatialAudio = true, screamerProtection = true;
    /** Null in Einstellungen von 0.8.1 und älter: wird aus {@link #spatialAudio} übernommen. */
    public SpatialMode spatialMode;
    public float screamerProtectionLevel = 75, screamerMaxVolume = 15;
    public boolean showHud = true, hudOnlySpeaking, showSpeakers = true, showNameIcons = true, showDistance = true, showHeads = true;
    public Corner hudCorner = Corner.BOTTOM_LEFT;
    public float hudScale = 1, hudOpacity = .85f;
    public int maxSpeakers = 5;
    public boolean notificationSounds = true;
    public Map<String, Player> players = new LinkedHashMap<>();
    /** Blockierte Spieler (UUID → Name, Datum). Sie werden nicht abgespielt und wirken, als hätten sie keinen VoiceChat. */
    public Map<String, Blocked> blocked = new LinkedHashMap<>();
    /** Mikrofon pausieren, solange ein Blockierter zuhören könnte (Server verteilen Sprache an alle in Reichweite). */
    public boolean blockPauseMic = true;
    public float blockPauseRange = 48;
    /** Zusätzlich am Namen erkennen (Offline-Server, Bedrock über Geyser: dort weicht die UUID ab). */
    public boolean blockMatchNames = true;
    public boolean blockHint = true;
    private transient Path file;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final class Player {
        public String name = "";
        public float volume = 1;
        public boolean muted;
        public boolean focused;
    }
    public Player player(UUID id, String name) {
        Player value = players.computeIfAbsent(id.toString(), ignored -> new Player());
        if (name != null && !name.isBlank()) value.name = name;
        return value;
    }
    public Player existing(UUID id) { return players.get(id.toString()); }
    public static final class Blocked {
        public String name = "";
        public long since;
    }
    public boolean isBlocked(UUID id) { return isBlocked(id, null); }
    public boolean isBlocked(UUID id, String name) {
        if (blocked.isEmpty()) return false;
        if (id != null && blocked.containsKey(id.toString())) return true;
        if (!blockMatchNames || name == null || name.isBlank()) return false;
        for (Blocked entry : blocked.values()) if (entry.name.equalsIgnoreCase(name)) return true;
        return false;
    }
    /** Blockiert {@code id}; ein bekannter Name wird übernommen, ein leerer ersetzt keinen gespeicherten. */
    public void block(UUID id, String name) {
        Blocked entry = blocked.computeIfAbsent(id.toString(), ignored -> { var created = new Blocked(); created.since = System.currentTimeMillis(); return created; });
        if (name != null && !name.isBlank()) entry.name = name.trim();
    }
    public boolean unblock(UUID id) { return blocked.remove(id.toString()) != null; }
    /** Hält gespeicherte Namen aktuell, wenn ein blockierter Spieler mit neuem Namen gesehen wird. */
    public boolean rename(UUID id, String name) {
        Blocked entry = blocked.get(id.toString());
        if (entry == null || name == null || name.isBlank() || entry.name.equals(name)) return false;
        entry.name = name; return true;
    }
    public static VoiceConfig load(Path file) {
        VoiceConfig config = new VoiceConfig();
        if (Files.exists(file)) {
            try {
                VoiceConfig parsed = JSON.fromJson(Files.readString(file), VoiceConfig.class);
                if (parsed == null) throw new IOException("Empty config");
                config = parsed;
            } catch (Exception error) {
                try { Files.copy(file, file.resolveSibling(file.getFileName() + ".invalid-" + System.currentTimeMillis())); }
                catch (IOException backupError) { throw new IllegalStateException("Ungültige Voice-Konfiguration konnte nicht gesichert werden", backupError); }
            }
        }
        config.file = file;
        config.sanitize();
        return config;
    }
    public void sanitize() {
        if (activation == null) activation = Activation.PUSH_TO_TALK;
        if (service == null) service = Service.AUTO;
        if (channelActivation == null) channelActivation = Activation.VOICE;
        if (spatialMode == null) spatialMode = spatialAudio ? SpatialMode.NORMAL : SpatialMode.OFF;
        spatialAudio = spatialMode != SpatialMode.OFF;
        focusedVolume = bound(focusedVolume, 0, 2, 1.5f);
        unfocusedVolume = bound(unfocusedVolume, 0, 2, .5f);
        if (hudCorner == null) hudCorner = Corner.BOTTOM_LEFT;
        if (inputDevice == null) inputDevice = "";
        if (outputDevice == null) outputDevice = "";
        if (inputBackend == null) inputBackend = AudioBackend.JAVAX;
        if (outputBackend == null) outputBackend = AudioBackend.JAVAX;
        if (!inputBackend.available()) { inputBackend = AudioBackend.JAVAX; inputDevice = ""; }
        if (!outputBackend.available()) { outputBackend = AudioBackend.JAVAX; outputDevice = ""; }
        if (!alternativeAudioInterfaces) {
            if (inputBackend != AudioBackend.JAVAX) { inputBackend = AudioBackend.JAVAX; inputDevice = ""; }
            if (outputBackend != AudioBackend.JAVAX) { outputBackend = AudioBackend.JAVAX; outputDevice = ""; }
        }
        microphoneVolume = bound(microphoneVolume, 0, 4, 1);
        outputVolume = bound(outputVolume, 0, 2, 1);
        activationThresholdDb = bound(activationThresholdDb, -70, -10, -40);
        gateThresholdDb = bound(gateThresholdDb, -80, -20, -55);
        releaseMillis = Math.clamp(releaseMillis, 0, 1000);
        surroundRange = bound(surroundRange, 5, 64, 18);
        screamerProtectionLevel = bound(screamerProtectionLevel, 20, 100, 75);
        screamerMaxVolume = bound(screamerMaxVolume, 1, 100, 15);
        hudScale = bound(hudScale, .5f, 2, 1);
        hudOpacity = bound(hudOpacity, .1f, 1, .85f);
        maxSpeakers = Math.clamp(maxSpeakers, 1, 12);
        if (players == null) players = new LinkedHashMap<>();
        players.entrySet().removeIf(e -> { try { UUID.fromString(e.getKey()); return e.getValue() == null; } catch (RuntimeException ignored) { return true; } });
        players.values().forEach(p -> { p.volume = bound(p.volume, 0, 2, 1); if (p.name == null) p.name = ""; });
        if (blocked == null) blocked = new LinkedHashMap<>();
        blocked.entrySet().removeIf(e -> { try { UUID.fromString(e.getKey()); return e.getValue() == null; } catch (RuntimeException ignored) { return true; } });
        blocked.values().forEach(b -> { if (b.name == null) b.name = ""; if (b.since < 0) b.since = 0; });
        blockPauseRange = bound(blockPauseRange, 8, 128, 48);
    }
    private static float bound(float value, float min, float max, float fallback) { return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback; }
    public void save() {
        if (file == null) return;
        sanitize();
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(this));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { throw new IllegalStateException("Voice-Einstellungen konnten nicht gespeichert werden", error); }
    }
    /**
     * Wie {@link #save()}, aber nur das Serialisieren passiert auf dem aufrufenden (Client-)Thread; das Schreiben
     * übernimmt {@code io}. Für häufige Änderungen aus dem Lumora-HUD-Editor.
     */
    public void saveAsync(java.util.concurrent.Executor io) {
        if (file == null) return;
        sanitize();
        Path target = file; String json = JSON.toJson(this);
        io.execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                Files.writeString(temporary, json);
                try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException error) { VoiceLog.warn("Voice-Einstellungen konnten nicht gespeichert werden", error); }
        });
    }
    public void reset() {
        Path path = file;
        var keptBlocked = new LinkedHashMap<>(blocked); // Die Blockliste ist keine Einstellung und bleibt erhalten.
        VoiceConfig fresh = new VoiceConfig();
        fresh.blocked = keptBlocked;
        try { for (var field : VoiceConfig.class.getFields()) if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(this, field.get(fresh)); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        file = path;
        save();
    }
    /** 3D-Audio-Stufe, auch vor dem ersten {@link #sanitize()}. */
    public SpatialMode spatial() { return spatialMode != null ? spatialMode : spatialAudio ? SpatialMode.NORMAL : SpatialMode.OFF; }
    public AudioSettings audioSettings() {
        return audioSettings(activation);
    }
    public AudioSettings audioSettings(Activation activation) {
        return new AudioSettings(inputBackend, outputBackend, inputDevice, outputDevice, activation, microphoneVolume, outputVolume,
                activationThresholdDb, autoThreshold, releaseMillis, highPassFilter, noiseGate, gateThresholdDb, automaticGain,
                screamerProtection, screamerProtectionLevel, screamerMaxVolume, noiseSuppression, volumeCompressor);
    }
    public record AudioSettings(AudioBackend inputBackend, AudioBackend outputBackend, String inputDevice, String outputDevice, Activation activation, float microphoneVolume,
            float outputVolume, float thresholdDb, boolean autoThreshold, int releaseMillis, boolean highPass, boolean noiseGate,
            float gateThresholdDb, boolean automaticGain, boolean protection, float protectionLevel, float maxVolume,
            boolean noiseSuppression, boolean volumeCompressor) {}
}
