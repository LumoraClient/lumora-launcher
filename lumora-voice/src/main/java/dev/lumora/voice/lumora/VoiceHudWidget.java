package dev.lumora.voice.lumora;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.voice.VoiceClient;
import dev.lumora.voice.VoiceConfig;
import dev.lumora.voice.ui.VoiceHudContent;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The voice status HUD as Lumora widget {@code lumoravoice:voice}. Position, size, layer and profiles come from the
 * Lumora HUD editor; the display options stay in {@link VoiceConfig} (lumoravoice.json) and are bound, not copied,
 * so the LumoraVoice settings card and the Lumora widget settings show the same values.
 */
public final class VoiceHudWidget extends HudWidget {
    private final VoiceClient voice;
    private final Supplier<String> pushToTalkKey;
    private final Runnable changed;
    private volatile VoiceHudContent.Content current;
    /** Size measured in the last frame (RenderContext has no font); estimated before the first frame. */
    private volatile int measuredWidth = -1;

    /** @param changed called after a display option changed (the addon saves lumoravoice.json debounced) */
    public VoiceHudWidget(VoiceClient voice, Supplier<String> pushToTalkKey, Runnable changed) {
        super("voice");
        this.voice = voice; this.pushToTalkKey = pushToTalkKey; this.changed = changed;
    }

    private VoiceConfig c() { return voice.config(); }

    /** Once per client tick on the client thread. */
    public void update(boolean inWorld) {
        current = VoiceHudContent.build(voice, inWorld, pushToTalkKey);
    }

    @Override public String nameKey() { return "lumoravoice.widget.voice"; }

    @Override public String category() { return WidgetCategory.VOICE; }

    /** First start: take over the corner and size chosen in LabyVoice so far. */
    @Override
    public WidgetPlacement defaultPlacement() {
        VoiceConfig cfg = c();
        boolean right = cfg.hudCorner == VoiceConfig.Corner.TOP_RIGHT || cfg.hudCorner == VoiceConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = cfg.hudCorner == VoiceConfig.Corner.BOTTOM_LEFT || cfg.hudCorner == VoiceConfig.Corner.BOTTOM_RIGHT;
        return new WidgetPlacement(right ? 0.985f : 0.015f, bottom ? 0.985f : 0.02f, cfg.hudScale, cfg.showHud);
    }

    @Override public float minScale() { return 0.5f; }

    @Override public float maxScale() { return 2.5f; }

    @Override
    public void configure(SettingsGroup s) {
        // own status line
        bind(s.toggle("indicator_text", true), () -> c().showIndicatorText, v -> c().showIndicatorText = v).section("lumoravoice.section.hud_status");
        bind(s.toggle("only_speaking", false), () -> c().hudOnlySpeaking, v -> c().hudOnlySpeaking = v);
        bind(s.toggle("block_hint", true), () -> c().blockHint, v -> c().blockHint = v);

        // speaker list: the options below only matter while the list is shown
        BooleanSetting speakers = bind(s.toggle("speakers", true), () -> c().showSpeakers, v -> c().showSpeakers = v).section("lumoravoice.section.hud_speakers");
        bind(s.slider("max_speakers", 5, 1, 12), () -> c().maxSpeakers, v -> c().maxSpeakers = v).requires(speakers);
        bind(s.toggle("hide_inactive", true), () -> c().hideInactive, v -> c().hideInactive = v).requires(speakers);
        bind(s.toggle("heads", true), () -> c().showHeads, v -> c().showHeads = v).requires(speakers);
        bind(s.toggle("distance", true), () -> c().showDistance, v -> c().showDistance = v).requires(speakers);
        bind(s.toggle("waves", true), () -> c().showAudioWaves, v -> c().showAudioWaves = v).requires(speakers);
        bind(s.toggle("colored_names", false), () -> c().coloredNames, v -> c().coloredNames = v).requires(speakers);

        // look
        bind(s.slider("opacity", 0.85, 0.1, 1.0, 0.05), () -> (double) c().hudOpacity, v -> c().hudOpacity = (float) (double) v).section("lumoravoice.section.hud_look");
    }

    private <T, S extends Setting<T>> S bind(S setting, Supplier<T> get, Consumer<T> set) {
        setting.bind(get, v -> { set.accept(v); changed.run(); });
        return setting;
    }

    private VoiceHudContent.Content content(RenderContext ctx) {
        var content = current;
        return content == null && ctx.preview() ? VoiceHudContent.preview(c(), pushToTalkKey.get()) : content;
    }

    /** Hidden while voice chat or its HUD is off (H key); the editor then shows a placeholder. */
    @Override public boolean isVisible(RenderContext ctx) { return content(ctx) != null; }

    @Override
    public int width(RenderContext ctx) {
        var content = content(ctx);
        if (content == null) return 120;
        if (measuredWidth > 0) return measuredWidth;
        int chars = c().showIndicatorText ? content.state().length() : 0;
        for (String label : content.labels()) chars = Math.max(chars, label.length() + 4);
        return 25 + chars * 6;
    }

    @Override
    public int height(RenderContext ctx) {
        var content = content(ctx);
        return content != null ? VoiceHudContent.height(content) : 25;
    }

    @Override
    public void render(HudGraphics g, RenderContext ctx) {
        var content = content(ctx);
        if (content == null) return;
        measuredWidth = VoiceHudContent.width(g, c(), content);
        VoiceHudContent.draw(g, c(), content, voice.audio()::wave);
    }
}
