package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.render.SkinFaces;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Voice status indicator with the list of speakers. The content is collected once per client tick
 * ({@link #build}) and drawn at (0, 0) by {@link #draw}; the Lumora HUD editor places and scales it.
 */
public final class VoiceHudContent {
    /** Everything one frame needs. */
    public record Content(String state, boolean speaking, List<VoiceClient.Nearby> people, List<String> labels, boolean disconnected, boolean heads, TextureRef icon) {
        /** Height of one speaker row: taller with player heads. */
        int rowPitch() { return heads ? HEAD_PITCH : 13; }
    }

    /** Head size (2x2 screen pixels per skin pixel) and row height with heads. */
    static final int HEAD = 16, HEAD_PITCH = HEAD + 4;

    /** Status icons (16x16 pixel art, drawn 1:1 in their own colors) and where the status text starts. */
    static final int ICON = 16, ICON_X = 4, ICON_Y = 4, TEXT_X = ICON_X + ICON + 3;

    /** Plug with a red slash (also next to names): voice is not connected. */
    static final TextureRef DISCONNECTED = icon("plug_disconnected");
    /** Microphone while you talk, smaller microphone while you are silent. */
    static final TextureRef MIC = icon("mic"), MIC_IDLE = icon("mic_idle");
    /** Microphone with a red slash: muted or paused for a blocked player. */
    static final TextureRef MIC_MUTED = icon("mic_muted");
    /** Microphone with an orange slash: muted by the voice service (Laby Voice moderation). */
    static final TextureRef MIC_SERVER_MUTED = icon("mic_server_muted");
    /** Speaker with a red slash: you hear nothing (deafened or speakers muted). */
    static final TextureRef SPEAKER_MUTED = icon("speaker_muted");

    private static TextureRef icon(String name) { return new TextureRef("lumoravoice", "textures/icon/" + name + ".png"); }

    private VoiceHudContent() {}

    /** The content for the current state, or {@code null} if the HUD is off or hidden right now. */
    public static Content build(VoiceClient voice, boolean inWorld, Supplier<String> pushToTalkKey) {
        VoiceConfig c = voice.config();
        if (!c.enabled || !c.showHud || !inWorld) return null;
        boolean speaking = voice.audio().talking();
        if (c.hudOnlySpeaking && !speaking) return null;
        String paused = c.blockHint ? voice.pausedFor() : null;
        String state = c.deafened ? "Audio stumm" : c.muted ? "Mikrofon stumm" : paused != null ? "Pausiert · " + paused + " (blockiert) in Hörweite"
                : c.outputMuted ? "Lautsprecher stumm" : voice.serverMuted() ? "Vom Dienst stummgeschaltet" : speaking ? "Du sprichst"
                : voice.connected() ? voice.serviceName() + " · " + pushToTalkKey.get() : "Voice nicht verbunden";
        var channels = voice.channels(); boolean channelMode = channels != null && !channels.current().proximity();
        var people = c.showSpeakers ? voice.nearby().stream().filter(p -> !c.hideInactive || channelMode || p.talking()).limit(c.maxSpeakers).toList() : List.<VoiceClient.Nearby>of();
        boolean disconnected = !voice.connected();
        return content(c, state, speaking, people, disconnected, statusIcon(c, disconnected, paused != null, voice.serverMuted(), speaking));
    }

    /** The icon for the status line, in the same priority as its text (but a lost connection comes first). */
    static TextureRef statusIcon(VoiceConfig c, boolean disconnected, boolean paused, boolean serverMuted, boolean speaking) {
        if (disconnected) return DISCONNECTED;
        if (c.deafened || c.outputMuted) return SPEAKER_MUTED;
        if (serverMuted) return MIC_SERVER_MUTED;
        if (c.muted || paused) return MIC_MUTED;
        return speaking ? MIC : MIC_IDLE;
    }

    private static Content content(VoiceConfig c, String state, boolean speaking, List<VoiceClient.Nearby> people, boolean disconnected, TextureRef icon) {
        List<String> labels = people.stream().map(p -> p.name() + (c.showDistance && p.distance() >= 0 ? " · " + Math.round(p.distance()) + " m" : "")).toList();
        return new Content(state, speaking, people, labels, disconnected, c.showHeads, icon);
    }

    /** Sample content for the Lumora HUD editor when no world is loaded (no voice data exists then). */
    public static Content preview(VoiceConfig c, String pushToTalkKey) {
        var people = List.of(new VoiceClient.Nearby(new UUID(0, 1), "Steve", 6, true, OptionalInt.empty()), new VoiceClient.Nearby(new UUID(0, 2), "Alex", 14, false, OptionalInt.empty()));
        return content(c, "Laby Voice · " + pushToTalkKey, false, people, false, MIC_IDLE);
    }

    public static int width(HudGraphics g, VoiceConfig c, Content content) {
        int labels = 0;
        for (String label : content.labels()) labels = Math.max(labels, g.textWidth(label));
        return Math.max(c.showIndicatorText ? g.textWidth(content.state()) + TEXT_X + 6 : TEXT_X + 2, labels + 25 + (c.showAudioWaves ? 25 : 0) + (content.heads() ? HEAD + 5 : 0));
    }

    public static int height(Content content) { return 25 + content.rowPitch() * content.people().size(); }

    /** Draws the content at (0, 0); {@code wave} gives the live level bars of a speaker. */
    public static void draw(HudGraphics g, VoiceConfig c, Content content, Function<UUID, float[]> wave) {
        int w = width(g, c, content), h = height(content);
        boolean speaking = content.speaking();
        int opacity = (int) (c.hudOpacity * 255);
        g.card(0, 0, w, h, 6, Ui.alpha(Theme.color("hud_background"), opacity), Ui.alpha(Theme.color("hud_border"), opacity));
        if (speaking) g.roundedRect(ICON_X - 2, ICON_Y - 2, ICON + 4, ICON + 4, 5, Ui.alpha(Ui.accent(), 0x50));
        g.texture(content.icon(), ICON_X, ICON_Y, ICON, ICON, ICON, ICON);
        if (c.showIndicatorText) Ui.label(g, content.state(), TEXT_X, 8, speaking ? Ui.accent() : Ui.text());
        var people = content.people();
        for (int i = 0; i < people.size(); i++) {
            var person = people.get(i); int rowY = 21 + i * content.rowPitch(), textX = 8;
            if (content.heads()) {
                // light frame while the person speaks, like the speaker list of the voice chat templates
                g.fill(7, rowY - 1, 8 + HEAD + 1, rowY + HEAD + 1, person.talking() ? 0xFFFFFFFF : Ui.alpha(0xFF000000, 0x80));
                SkinFaces.draw(g, person.id(), person.name(), 8, rowY, HEAD);
                textX += HEAD + 5; rowY += (HEAD - 8) / 2;
            }
            int color = c.coloredNames && person.teamColor().isPresent() ? 0xFF000000 | person.teamColor().getAsInt() : person.talking() ? Ui.accent() : Ui.muted();
            Ui.label(g, content.labels().get(i), textX, rowY, color);
            if (c.showAudioWaves) {
                float[] bars = wave.apply(person.id());
                for (int bar = 0; bar < bars.length; bar++) {
                    int barHeight = 1 + (int) (Math.sqrt(bars[bar]) * 8);
                    g.fill(w - 24 + bar * 3, rowY + 8 - barHeight, w - 22 + bar * 3, rowY + 8, person.talking() ? Ui.accent() : Theme.color("wave_idle"));
                }
            }
        }
    }
}
