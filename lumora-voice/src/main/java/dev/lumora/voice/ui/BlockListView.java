package dev.lumora.voice.ui;

import dev.lumora.voice.VoiceClient;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "Blockliste" (button in the settings card): everyone you blocked, also players who are offline; a click removes
 * the block, "Hinzufügen …" blocks by name or UUID. Players you see are blocked faster in the player menu.
 */
public final class BlockListView extends ListDialog {
    private static final DateTimeFormatter DATES = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final VoiceClient voice;

    public BlockListView(VoiceClient voice, ViewHost host) {
        super(host, "Blockliste");
        this.voice = voice;
        // keep names current (name changes) for everyone who is online
        boolean renamed = false;
        for (var online : voice.platform().tabList()) renamed |= voice.config().rename(online.id(), online.name());
        if (renamed) voice.save();
    }

    @Override
    protected Object state() { return List.copyOf(voice.config().blocked.keySet()); }

    @Override
    protected List<Control> entries() {
        List<Control> out = new ArrayList<>();
        for (var entry : new ArrayList<>(voice.config().blocked.entrySet())) {
            UUID id = UUID.fromString(entry.getKey());
            var value = entry.getValue();
            String name = value.name.isBlank() ? id.toString().substring(0, 8) : value.name;
            String since = value.since > 0 ? " · seit " + DATES.format(Instant.ofEpochMilli(value.since).atZone(ZoneId.systemDefault())) : "";
            out.add(new Control(name + since, id.toString(), () -> "Entfernen  ›", () -> unblock(id, name)));
        }
        return out;
    }

    private void unblock(UUID id, String name) {
        voice.config().unblock(id);
        voice.save();
        info = name + " ist nicht mehr blockiert."; error = "";
        refresh();
    }

    @Override
    protected String emptyText() { return "Niemand blockiert. Im Spiel: Mausrad auf den Spieler › VoiceChat."; }

    @Override
    protected List<Control> buttons() {
        return List.of(new Control("Hinzufügen …", "Spieler per Name oder UUID blockieren", () -> "",
                () -> host.open(new BlockPromptView(voice, host, message -> { info = message; error = ""; refresh(); }))).primary());
    }
}
