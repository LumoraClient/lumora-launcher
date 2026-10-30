package dev.lumora.example.timer;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.config.TextSetting;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.RenderContext;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

/**
 * Time since joining the current world. The text is rebuilt in {@link #tick()} (20x per second) and only drawn in
 * {@link #render}, so the frame itself allocates nothing.
 */
public final class SessionTimerWidget extends HudWidget {
    private final GameContext game;
    private TextSetting label;
    private ChoiceSetting format;
    private BooleanSetting background;
    private ColorSetting color;
    private long startedAt = -1;
    private String text = "00:12:34";
    private int textWidth = -1;

    public SessionTimerWidget(GameContext game) {
        super("timer");
        this.game = game;
    }

    @Override
    public String category() {
        return WidgetCategory.TIME;
    }

    @Override
    public WidgetPlacement defaultPlacement() {
        // top center, shown right away
        return WidgetPlacement.at(0.5f, 0.01f).enabledByDefault();
    }

    @Override
    public void configure(SettingsGroup settings) {
        label = settings.text("label", "Session", 24);
        format = settings.choice("format", "hms", "hms", "hm", "minutes");
        background = settings.toggle("background", true);
        color = settings.color("color", Colors.ACCENT_HOVER, false);
    }

    void start() {
        startedAt = System.currentTimeMillis();
    }

    void stop() {
        startedAt = -1;
    }

    @Override
    public void tick() {
        long seconds = startedAt < 0 ? 754 : (System.currentTimeMillis() - startedAt) / 1000;
        text = switch (format.get()) {
            case "hm" -> String.format("%d:%02d", seconds / 3600, seconds / 60 % 60);
            case "minutes" -> (seconds / 60) + " min";
            default -> String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        };
        textWidth = -1;
    }

    @Override
    public boolean isVisible(RenderContext context) {
        return game.inWorld() || context.preview();
    }

    private String line() {
        return label.get().isEmpty() ? text : label.get() + "  " + text;
    }

    @Override
    public int width(RenderContext context) {
        return 12 + (textWidth >= 0 ? textWidth : line().length() * 6);
    }

    @Override
    public int height(RenderContext context) {
        return 17;
    }

    @Override
    public void render(HudGraphics g, RenderContext context) {
        String l = line();
        if (textWidth < 0) {
            textWidth = g.textWidth(l);
        }
        int w = 12 + textWidth;
        if (background.get()) {
            g.card(0, 0, w, 17, 4, Colors.withAlpha(Colors.BACKGROUND, 0xB0), Colors.withAlpha(Colors.BORDER, 0xC0));
        }
        if (!label.get().isEmpty()) {
            g.text(label.get(), 6, 5, Colors.MUTED, false);
            g.text(text, 6 + g.textWidth(label.get() + "  "), 5, color.argb(), false);
        } else {
            g.text(text, 6, 5, color.argb(), false);
        }
    }
}
