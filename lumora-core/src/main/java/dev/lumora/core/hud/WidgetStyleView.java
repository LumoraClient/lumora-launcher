package dev.lumora.core.hud;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.ui.WidgetStyle;

/** {@link WidgetDesign} for addons ({@code AddonContext.theme().widgetStyle()}); Lumora's defaults while it is off. */
public final class WidgetStyleView implements WidgetStyle {
    public static final WidgetStyleView INSTANCE = new WidgetStyleView();

    private WidgetStyleView() {
    }

    @Override
    public boolean active() {
        return WidgetDesign.active();
    }

    @Override
    public boolean background() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.background() : l.background();
    }

    @Override
    public int backgroundColor() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.backgroundColor() : l.backgroundColor();
    }

    @Override
    public int cornerRadius() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.cornerRadius() : l.radius();
    }

    @Override
    public boolean border() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.border() : l.border();
    }

    @Override
    public int borderColor() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.borderColor() : l.borderNow();
    }

    @Override
    public boolean accentBar() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.accentBar() : l.accentBar();
    }

    @Override
    public int accentColor() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? dev.lumora.core.theme.LumoraTheme.accent() : l.accent();
    }

    @Override
    public int labelColor() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? dev.lumora.core.theme.LumoraTheme.accentHover() : l.label();
    }

    @Override
    public int valueColor() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.valueColor() : l.value();
    }

    @Override
    public boolean textShadow() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.textShadow() : l.shadow();
    }

    @Override
    public String format() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.format() : l.format();
    }

    @Override
    public boolean uppercaseLabels() {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.uppercaseLabels() : l.uppercase();
    }

    @Override
    public float scale() {
        return WidgetDesign.scale();
    }

    @Override
    public String label(String label) {
        WidgetDesign.Look l = WidgetDesign.look();
        return l == null ? NONE.label(label) : l.label(label == null ? "" : label);
    }

    @Override
    public void panel(HudGraphics g, int width, int height) {
        WidgetDesign.panel(g, width, height);
    }

    @Override
    public int lineWidth(HudGraphics g, String label, String value) {
        return WidgetDesign.lineWidth(g::textWidth, label == null ? "" : label, value == null ? "" : value);
    }

    @Override
    public void line(HudGraphics g, int x, int y, String label, String value) {
        WidgetDesign.line(g, x, y, label == null ? "" : label, value == null ? "" : value, labelColor(), valueColor(),
                textShadow(), false);
    }
}
