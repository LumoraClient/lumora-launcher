package dev.lumora.voice.ui;

import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.platform.VoicePlatform;
import java.util.List;
import java.util.UUID;

/**
 * LumoraVoice in the Lumora player menu (click the mouse wheel on a player): one entry "VoiceChat" that opens the
 * player's voice window ({@link PlayerVoiceView}) with volume, mute, focus and block. The chat menu of a message
 * (right click or "⋯") opens the same window for its sender ({@link #chatTarget}).
 */
public final class PlayerActions {
    static final float MAX_VOLUME = 2;

    private PlayerActions() { }

    public static List<PlayerMenuEntry> entries(VoiceClient voice, ViewHost host) {
        return List.of(PlayerMenuEntry.builder("voicechat", "VoiceChat").icon("♪").order(70)
                .onSelect(t -> host.open(window(voice, host, t.id(), t.name()))).build());
    }

    /** The voice window of one player (also used by the client's smoke test). */
    public static View window(VoiceClient voice, ViewHost host, UUID id, String name) { return new PlayerVoiceView(voice, host, id, name); }

    /**
     * The player behind a chat message (the chat menu only knows the sender's name): online players first, then
     * players you have settings for or blocked. Empty for server messages, your own messages and unknown names.
     */
    public static java.util.Optional<VoicePlatform.Named> chatTarget(VoiceClient voice, String sender) {
        if (sender == null || sender.isBlank()) return java.util.Optional.empty();
        String name = sender.strip();
        UUID self = voice.platform().profile().map(p -> p.id()).orElse(null);
        String selfName = voice.platform().profile().map(p -> p.name()).orElse("");
        if (name.equalsIgnoreCase(selfName)) return java.util.Optional.empty();
        for (var p : voice.platform().tabList()) {
            if (p.name().equalsIgnoreCase(name) && !p.id().equals(self)) return java.util.Optional.of(p);
        }
        for (var e : voice.config().players.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return java.util.Optional.of(new VoicePlatform.Named(UUID.fromString(e.getKey()), e.getValue().name));
        }
        for (var e : voice.config().blocked.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return java.util.Optional.of(new VoicePlatform.Named(UUID.fromString(e.getKey()), e.getValue().name));
        }
        return java.util.Optional.empty();
    }

    static float volume(VoiceClient voice, UUID id) {
        var p = voice.config().existing(id);
        return p == null ? 1 : p.volume;
    }
}
