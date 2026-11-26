package dev.lumora.voice.protocol;

import java.util.*;

/** Gemeinsame Sicht auf einen Sprachdienst (Laby Voice oder Simple Voice Chat); Audio sind immer 20-ms-Opus-Frames. */
public interface VoiceService extends AutoCloseable {
    /** Anzeigename für Status und HUD. */
    String name();
    boolean connected();
    boolean serverMuted();
    /** Ein kodierter 20-ms-Opus-Frame vom Mikrofon. */
    void audio(byte[] opus);
    void silenceAudio();
    /** Spieler, die der Client gerade sieht (Laby braucht das für die Weiterleitung). */
    default void visible(Set<UUID> ids) {}
    boolean participant(UUID id);
    /** Der Dienst meldet, dass dieser Spieler Voice hat, aber gerade nicht verbunden ist (Simple Voice Chat). */
    default boolean disconnected(UUID id) { return false; }
    /** Der Dienst hat diesen Spieler stummgeschaltet (Laby Voice meldet das für jeden Spieler). */
    default boolean serverMuted(UUID id) { return false; }
    /** Der Spieler ist in einer Gruppe bzw. einem Sprachkanal (nicht Umgebung), in dem du nicht bist: du hörst ihn nicht. */
    default boolean otherGroup(UUID id) { return false; }
    /** Laby-Sprachkanäle; {@code null}, wenn der Dienst keine hat. */
    default VoiceChannels channels() { return null; }
    /** Laby-Steuerpaket; andere Dienste ignorieren es. */
    default void control(byte[] payload) {}
    /** Hörweite für diesen Sprecher in Blöcken, oder {@code -1} für die eigene Einstellung. */
    default float range(UUID id) { return -1; }
    /** Sprecher ohne Position (Gruppen, Zuschauer), die unabhängig von der Entfernung hörbar sein sollen. */
    default Map<UUID, String> unpositioned() { return Map.of(); }
    /** Eigener Stummschalt-/Taub-Status hat sich geändert. */
    default void localState(boolean inputMuted, boolean outputMuted) {}
    @Override void close();
}
