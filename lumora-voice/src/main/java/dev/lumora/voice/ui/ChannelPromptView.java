package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.protocol.VoiceChannels;
import dev.lumora.voice.protocol.VoicePackets;
import dev.lumora.voice.protocol.VoiceProtocol;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Creates a Laby voice channel, or joins a password-protected one. */
final class ChannelPromptView extends Dialog {
    private static final int HEIGHT = 232;
    private final VoiceClient voice;
    private final UUID channel;
    private TextField name, password;
    private boolean proximity;

    static ChannelPromptView create(VoiceClient voice, ViewHost host) { return new ChannelPromptView(voice, host, null, "Sprachkanal erstellen"); }

    static ChannelPromptView join(VoiceClient voice, ViewHost host, UUID channel, String title) { return new ChannelPromptView(voice, host, channel, title); }

    private ChannelPromptView(VoiceClient voice, ViewHost host, UUID channel, String title) {
        super(host, title, HEIGHT);
        this.voice = voice; this.channel = channel;
    }

    @Override
    protected void layout() {
        password = new TextField(64, true, host::clipboard).bounds(x + 24, y + 105, w - 48);
        if (channel == null) {
            name = new TextField(32, false, host::clipboard).bounds(x + 24, y + 61, w - 48);
            fields.add(name);
            controls.add(new Control("Näherungsmodus", "Entfernung und Richtung innerhalb dieses Kanals berücksichtigen.", () -> proximity ? "An" : "Aus", () -> proximity = !proximity)
                    .toggle(() -> proximity).bounds(x + 18, y + 132, w - 36, 48));
        }
        fields.add(password);
        controls.add(new Control(channel == null ? "Erstellen" : "Beitreten", "An den Sprachdienst senden", () -> "", this::submit).primary().bounds(x + w - 136, y + 194, 118, 24));
        controls.add(new Control("Abbrechen", "Zurück zu den Kanälen", () -> "", host::back).compact().bounds(x + 18, y + 194, 106, 24));
    }

    @Override
    protected void submit() {
        try {
            if (channel == null && name.value().isBlank()) { error = "Bitte einen Kanalnamen eingeben."; return; }
            String secret = password.value();
            String hashed = secret.isEmpty() ? "" : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(secret.getBytes(StandardCharsets.UTF_8)));
            UUID self = voice.platform().profile().map(p -> p.id()).orElse(null);
            if (self == null) { error = "Keine gültige Minecraft-Sitzung."; return; }
            if (channel == null) {
                var server = voice.platform().server().map(s -> s.address().trim()).orElse("");
                if (server.isEmpty()) { error = "Kanäle gibt es nur auf einem Multiplayer-Server."; return; }
                voice.control(VoicePackets.CREATE_CHANNEL, out -> VoiceChannels.properties(out, createProperties(name.value().trim(), self, server, hashed, proximity)));
            } else {
                voice.control(VoicePackets.MOVE_USER, out -> { VoiceProtocol.uuid(out, self); VoiceProtocol.uuid(out, channel); VoiceProtocol.string(out, secret); });
            }
            password.setValue("");
            host.back();
        } catch (Exception failure) { error = "Kanalaktion konnte nicht vorbereitet werden."; }
    }

    /**
     * Properties of a new channel as the LabyMod voice chat sends them: name, owner, visibility, the server it belongs to
     * and proximity; the password (SHA-1, hex) only when one is set. Without owner and server the voice service ignores
     * a channel that is visible on the server only.
     */
    static Map<String, String> createProperties(String name, UUID owner, String server, String hashedPassword, boolean proximity) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("name", name);
        properties.put("owner", owner.toString());
        properties.put("visibility", "SERVER");
        properties.put("serverAddress", server);
        properties.put("proximity", Boolean.toString(proximity));
        if (!hashedPassword.isEmpty()) properties.put("password", hashedPassword);
        return properties;
    }

    @Override
    protected void drawContent(HudGraphics g) {
        Ui.label(g, channel == null ? "Kanalname" : "Passwortgeschützter Kanal", x + 18, y + 40, Ui.muted());
        if (channel == null) drawField(g, name, x + 18, y + 55, w - 36, "z. B. Bauteam");
        Ui.label(g, "Passwort" + (channel == null ? " (optional)" : ""), x + 18, y + 84, Ui.muted());
        drawField(g, password, x + 18, y + 99, w - 36, "");
        drawMessage(g, y + 181);
    }

    @Override
    public void removed() { if (password != null) password.setValue(""); }
}
