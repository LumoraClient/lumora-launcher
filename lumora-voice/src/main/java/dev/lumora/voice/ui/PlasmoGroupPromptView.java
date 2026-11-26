package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.plasmo.PlasmoGroups;

/** Creates a Plasmo Voice group (pv-addon-groups), or joins a password-protected one. */
final class PlasmoGroupPromptView extends Dialog {
    private static final int HEIGHT = 190;
    private final VoiceClient voice;
    private final PlasmoGroups.Group group;
    private TextField name, password;

    static PlasmoGroupPromptView create(VoiceClient voice, ViewHost host) { return new PlasmoGroupPromptView(voice, host, null, "Gruppe erstellen"); }

    static PlasmoGroupPromptView join(VoiceClient voice, ViewHost host, PlasmoGroups.Group group) { return new PlasmoGroupPromptView(voice, host, group, group.name()); }

    private PlasmoGroupPromptView(VoiceClient voice, ViewHost host, PlasmoGroups.Group group, String title) {
        super(host, title, HEIGHT);
        this.voice = voice; this.group = group;
    }

    private int maxName() { var groups = voice.plasmoGroups(); return groups == null ? 16 : groups.capabilities().maxName(); }

    @Override
    protected void layout() {
        password = new TextField(64, true, host::clipboard).bounds(x + 24, y + 105, w - 48);
        if (group == null) {
            name = new TextField(maxName(), false, host::clipboard).bounds(x + 24, y + 61, w - 48);
            fields.add(name);
        }
        fields.add(password);
        controls.add(new Control(group == null ? "Erstellen" : "Beitreten", "An den Server senden", () -> "", this::submit).primary().bounds(x + w - 136, y + 152, 118, 24));
        controls.add(new Control("Abbrechen", "Zurück zu den Gruppen", () -> "", host::back).compact().bounds(x + 18, y + 152, 106, 24));
    }

    @Override
    protected void submit() {
        var groups = voice.plasmoGroups();
        if (groups == null) { error = "Keine Verbindung zu den Gruppen des Servers."; return; }
        String secret = password.value();
        if (group == null) {
            String title = name.value().trim();
            var caps = groups.capabilities();
            if (!title.isEmpty() && (title.length() < caps.minName() || title.length() > caps.maxName())) {
                error = "Name: " + caps.minName() + " bis " + caps.maxName() + " Zeichen."; return;
            }
            groups.create(title, secret);
        } else {
            if (secret.isEmpty()) { error = "Bitte das Passwort eingeben."; return; }
            groups.join(group.id(), secret);
        }
        password.setValue("");
        host.back();
    }

    @Override
    protected void drawContent(HudGraphics g) {
        Ui.label(g, group == null ? "Gruppenname (leer = Standardname des Servers)" : "Passwortgeschützte Gruppe", x + 18, y + 40, Ui.muted());
        if (group == null) drawField(g, name, x + 18, y + 55, w - 36, "z. B. Bauteam");
        Ui.label(g, "Passwort" + (group == null ? " (optional)" : ""), x + 18, y + 84, Ui.muted());
        drawField(g, password, x + 18, y + 99, w - 36, "");
        drawMessage(g, y + 136);
    }

    @Override
    public void removed() { if (password != null) password.setValue(""); }
}
