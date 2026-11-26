package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.svc.SvcGroups;

/** Creates a Simple Voice Chat group, or joins a password-protected one. */
final class GroupPromptView extends Dialog {
    private static final int HEIGHT = 232;
    private final VoiceClient voice;
    private final SvcGroups.Group group;
    private TextField name, password;
    private SvcGroups.Type type = SvcGroups.Type.NORMAL;

    static GroupPromptView create(VoiceClient voice, ViewHost host) { return new GroupPromptView(voice, host, null, "Gruppe erstellen"); }

    static GroupPromptView join(VoiceClient voice, ViewHost host, SvcGroups.Group group) { return new GroupPromptView(voice, host, group, group.name()); }

    private GroupPromptView(VoiceClient voice, ViewHost host, SvcGroups.Group group, String title) {
        super(host, title, HEIGHT);
        this.voice = voice; this.group = group;
    }

    @Override
    protected void layout() {
        password = new TextField(group == null ? SvcGroups.MAX_PASSWORD : 512, true, host::clipboard).bounds(x + 24, y + 105, w - 48);
        if (group == null) {
            name = new TextField(SvcGroups.MAX_NAME, false, host::clipboard).bounds(x + 24, y + 61, w - 48);
            fields.add(name);
            controls.add(new Control("Typ", "Klicken wechselt: Normal, Offen, Isoliert", () -> type.label() + "  ›", () -> {
                type = type.next();
                info = type.description(); error = "";
            }).bounds(x + 18, y + 132, w - 36, 48));
        }
        fields.add(password);
        controls.add(new Control(group == null ? "Erstellen" : "Beitreten", "An den Server senden", () -> "", this::submit).primary().bounds(x + w - 136, y + 194, 118, 24));
        controls.add(new Control("Abbrechen", "Zurück zu den Gruppen", () -> "", host::back).compact().bounds(x + 18, y + 194, 106, 24));
    }

    @Override
    protected void submit() {
        String secret = password.value();
        if (group == null) {
            String title = name.value().trim();
            if (!SvcGroups.validName(title)) { error = "Name: 1 bis 24 Zeichen, ohne Steuerzeichen."; return; }
            if (!SvcGroups.validPassword(secret)) { error = "Passwort: bis 24 Zeichen, nicht mit Leerzeichen beginnend."; return; }
            voice.createGroup(title, secret, type);
        } else {
            if (secret.isEmpty()) { error = "Bitte das Passwort eingeben."; return; }
            voice.joinGroup(group.id(), secret);
        }
        password.setValue("");
        host.back();
    }

    @Override
    protected void drawContent(HudGraphics g) {
        Ui.label(g, group == null ? "Gruppenname" : "Passwortgeschützte Gruppe", x + 18, y + 40, Ui.muted());
        if (group == null) drawField(g, name, x + 18, y + 55, w - 36, "z. B. Bauteam");
        Ui.label(g, "Passwort" + (group == null ? " (optional)" : ""), x + 18, y + 84, Ui.muted());
        drawField(g, password, x + 18, y + 99, w - 36, "");
        drawMessage(g, y + 181);
    }

    @Override
    public void removed() { if (password != null) password.setValue(""); }
}
