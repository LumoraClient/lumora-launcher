package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.render.NameTagState;
import java.util.UUID;

/**
 * "VoiceChat" from the player menu: everything about one player in one window, like the volume screen of Simple Voice
 * Chat. Volume slider (0 to 200 %, only for you), mute, focus, block and reset, with the player's voice status.
 */
final class PlayerVoiceView extends Dialog {
    static final int HEIGHT = 212;
    private final VoiceClient voice;
    private final UUID player;
    private final String name;
    private Control slider;

    PlayerVoiceView(VoiceClient voice, ViewHost host, UUID player, String name) {
        super(host, "VoiceChat", HEIGHT);
        this.voice = voice; this.player = player; this.name = displayName(voice, player, name);
    }

    /** The given name, else the tab list name, else the start of the UUID (some servers send no name). */
    static String displayName(VoiceClient voice, UUID player, String name) {
        if (name != null && !name.isBlank()) return name.strip();
        for (var p : voice.platform().tabList()) if (p.id().equals(player) && !p.name().isBlank()) return p.name();
        var known = voice.config().existing(player);
        if (known != null && !known.name.isBlank()) return known.name;
        return player.toString().substring(0, 8);
    }

    UUID player() { return player; }

    String name() { return name; }

    Control slider() { return slider; }

    @Override
    protected String title() { return "VoiceChat · " + name; }

    private float volume() { return PlayerActions.volume(voice, player); }

    private void volume(double value) {
        voice.config().player(player, name).volume = (float) Math.clamp(value, 0, PlayerActions.MAX_VOLUME);
    }

    private boolean muted() { var p = voice.config().existing(player); return p != null && p.muted; }

    private boolean focused() { var p = voice.config().existing(player); return p != null && p.focused; }

    private boolean blocked() { return voice.blocked(player, name); }

    @Override
    protected void layout() {
        slider = new Control("Lautstärke", "Nur für dich: wie laut du " + name + " hörst.", () -> Math.round(volume() * 100) + " %", () -> { })
                .slider(this::volume, this::volume, 0, PlayerActions.MAX_VOLUME, .05, voice::save).bounds(x + 18, y + 40, w - 36, 48);
        controls.add(slider);
        int half = (w - 36 - 8) / 2, left = x + 18, right = left + half + 8;
        controls.add(button(muted() ? "Stummschaltung aufheben" : "Stummschalten", "Nur du hörst " + name + " nicht mehr.", this::muted,
                () -> { var p = voice.config().player(player, name); p.muted = !p.muted; voice.save(); }).bounds(left, y + 96, half, 24));
        controls.add(button(focused() ? "Fokus aufheben" : "Fokussieren", "Hebt " + name + " hervor und senkt die übrigen Stimmen ab.", this::focused,
                () -> { var p = voice.config().player(player, name); p.focused = !p.focused; voice.save(); }).bounds(right, y + 96, half, 24));
        controls.add(button(blocked() ? "Entblockieren" : "Blockieren", "Blockierte hörst du nicht, und sie hören dich nicht.", this::blocked,
                () -> voice.toggleBlock(player, name)).bounds(left, y + 126, half, 24));
        controls.add(button("Zurücksetzen", "Lautstärke 100 %, nicht stumm, kein Fokus", () -> false,
                () -> { voice.config().players.remove(player.toString()); voice.save(); }).bounds(right, y + 126, half, 24));
        int doneWidth = Math.min(160, w - 36);
        controls.add(new Control("Fertig", "Fenster schließen", () -> "", host::back).primary()
                .bounds(x + (w - doneWidth) / 2, y + HEIGHT - 34, doneWidth, 24));
    }

    /** A toggle-like button: runs {@code action}, then shows the new state (label and highlight). */
    private Control button(String label, String description, java.util.function.BooleanSupplier on, Runnable action) {
        return new Control(label, description, () -> "", () -> { action.run(); controls.clear(); layout(); }).compact().toggle(on);
    }

    @Override
    protected void submit() { host.back(); }

    @Override
    protected void drawContent(HudGraphics g) {
        NameTagState s = voice.nameTagState(player);
        String state = switch (s) {
            case SPEAKING -> "Spricht gerade";
            case IDLE -> "Im Voice-Chat";
            case MUTED -> "Im Voice-Chat · von dir stummgeschaltet";
            case SERVER_MUTED -> "Vom Sprachdienst stummgeschaltet";
            case GROUP -> {
                String channel = voice.channelOf(player);
                yield channel.isEmpty() ? "In einer anderen Gruppe" : "In einer anderen Gruppe · " + channel;
            }
            case DISCONNECTED -> "Voice-Verbindung verloren";
            case ABSENT -> blocked() ? "Blockiert" : "Nicht im Voice-Chat";
        };
        int color = s == NameTagState.SPEAKING ? Ui.accent() : s == NameTagState.DISCONNECTED ? Theme.color("error") : Ui.muted();
        Ui.label(g, Ui.fit(g, state, w - 36), x + 18, y + 160, color);
    }
}
