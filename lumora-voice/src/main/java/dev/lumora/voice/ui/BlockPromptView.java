package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.BlockLookup;
import dev.lumora.voice.VoiceClient;
import java.util.Optional;
import java.util.UUID;

/** Adds a player to the block list by name or UUID. */
final class BlockPromptView extends Dialog {
    private static final int HEIGHT = 176;
    private final VoiceClient voice;
    private final java.util.function.Consumer<String> onBlocked;
    private TextField input;
    private boolean busy, closed;
    /** Result of the Mojang lookup, applied on the render thread. */
    private volatile Runnable pending;

    BlockPromptView(VoiceClient voice, ViewHost host, java.util.function.Consumer<String> onBlocked) {
        super(host, "Spieler blockieren", HEIGHT);
        this.voice = voice; this.onBlocked = onBlocked;
    }

    @Override
    protected void layout() {
        input = new TextField(36, false, host::clipboard).bounds(x + 24, y + 61, w - 48);
        fields.add(input);
        controls.add(new Control("Blockieren", "Zur Blockliste hinzufügen", () -> "", this::submit).primary().bounds(x + w - 136, y + 138, 118, 24));
        controls.add(new Control("Abbrechen", "Zurück zur Blockliste", () -> "", host::back).compact().bounds(x + 18, y + 138, 106, 24));
    }

    String value() { return input == null ? "" : input.value(); }

    @Override
    protected void submit() {
        if (busy || input == null) return;
        String value = input.value().trim(); error = ""; info = "";
        UUID id = BlockLookup.parseUuid(value);
        if (id != null) { done(id, onlineName(id)); return; }
        if (!BlockLookup.validName(value)) { error = "Bitte einen Minecraft-Namen oder eine UUID eingeben."; return; }
        for (var online : voice.platform().tabList()) if (online.name().equalsIgnoreCase(value)) { done(online.id(), online.name()); return; }
        busy = true; info = "„" + value + "“ wird bei Mojang nachgeschlagen …";
        BlockLookup.mojang(value).whenComplete((result, failure) -> pending = () -> lookedUp(value, result, failure));
    }

    private void lookedUp(String value, Optional<BlockLookup.Profile> result, Throwable failure) {
        busy = false;
        if (closed) return;
        if (failure != null) { error = "Mojang nicht erreichbar – UUID eingeben oder später erneut."; info = ""; return; }
        if (result.isPresent()) { done(result.get().id(), result.get().name()); return; }
        if (voice.config().blockMatchNames) done(BlockLookup.offlineUuid(value), value);
        else { error = "Name unbekannt. „Auch am Namen erkennen“ einschalten."; info = ""; }
    }

    private String onlineName(UUID id) {
        for (var online : voice.platform().tabList()) if (online.id().equals(id)) return online.name();
        return "";
    }

    private void done(UUID id, String name) {
        if (voice.platform().profile().map(p -> p.id().equals(id)).orElse(false)) { error = "Du kannst dich nicht selbst blockieren."; return; }
        voice.config().block(id, name); voice.save();
        onBlocked.accept((name.isBlank() ? id.toString().substring(0, 8) : name) + " ist blockiert.");
        host.back();
    }

    @Override
    public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        Runnable result = pending;
        if (result != null) { pending = null; result.run(); }
        super.render(g, width, height, mouseX, mouseY);
    }

    @Override
    protected void drawContent(HudGraphics g) {
        Ui.label(g, "Name oder UUID", x + 18, y + 40, Ui.muted());
        drawField(g, input, x + 18, y + 55, w - 36, "z. B. Notch");
        Ui.label(g, Ui.fit(g, "Ihr hört euch gegenseitig nicht mehr.", w - 36), x + 18, y + 86, Ui.muted());
        drawMessage(g, y + 108);
    }

    @Override
    public void removed() { closed = true; }
}
