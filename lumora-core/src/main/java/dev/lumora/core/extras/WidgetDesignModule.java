package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.Colors;
import dev.lumora.core.hud.WidgetDesign;

/**
 * Built-in module "Widget-Design": one look for all HUD widgets, set in one place. While it is on, its settings
 * replace the look settings of every widget (background, border, corners, accent stripe, colours, text shadow, format
 * of label and value, size); the widgets hide their own look settings. Off by default, so nobody's widgets change.
 */
public final class WidgetDesignModule implements LumoraAddon {
    static final String K = "lumora.extras.widgetdesign.";
    static final int BACKGROUND = Colors.withAlpha(Colors.BACKGROUND, 0x9C);
    static final int BORDER = 0x40FFFFFF;

    BooleanSetting background;
    ColorSetting backgroundColor;
    IntegerSetting corners;
    BooleanSetting border;
    ColorSetting borderColor;
    BooleanSetting accentBar;
    BooleanSetting accentTheme;
    ColorSetting accentColor;
    ChoiceSetting format;
    BooleanSetting uppercase;
    BooleanSetting labelTheme;
    ColorSetting labelColor;
    ColorSetting valueColor;
    BooleanSetting shadow;
    DecimalSetting scale;

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        s.button("preset_lumora", this::lumora).label(K + "apply").name(K + "preset_lumora").section(K + "section.presets");
        s.button("preset_minimal", this::minimal).label(K + "apply").name(K + "preset_minimal");
        s.button("preset_rounded", this::rounded).label(K + "apply").name(K + "preset_rounded");

        background = s.toggle("background", true).name(K + "background").section(K + "section.look");
        backgroundColor = s.color("background_color", BACKGROUND, true).name(K + "background_color").requires(background);
        corners = s.slider("corners", 0, 0, 6).unit(" px").name(K + "corners");
        border = s.toggle("border", false).name(K + "border");
        borderColor = s.color("border_color", BORDER, true).name(K + "border_color").requires(border);
        accentBar = s.toggle("accent_bar", true).name(K + "accent_bar");
        accentTheme = s.checkbox("accent_theme", true).name(K + "accent_theme").requires(accentBar);
        accentColor = s.chroma("accent_color", Colors.ACCENT, false).name(K + "accent_color")
                .visibleWhen(() -> accentBar.get() && !accentTheme.get());

        format = s.dropdown("format", WidgetDesign.SPACE, WidgetDesign.SPACE, WidgetDesign.COLON,
                WidgetDesign.BRACKETS, WidgetDesign.VALUE_FIRST, WidgetDesign.VALUE_ONLY).name(K + "format").section(K + "section.text");
        uppercase = s.toggle("uppercase", false).name(K + "uppercase");
        shadow = s.toggle("shadow", true).name(K + "shadow");
        labelTheme = s.toggle("label_theme", true).name(K + "label_theme");
        labelColor = s.chroma("label_color", Colors.ACCENT_HOVER, false).name(K + "label_color")
                .visibleWhen(() -> !labelTheme.get());
        valueColor = s.chroma("value_color", Colors.TEXT, false).name(K + "value_color");

        scale = s.slider("scale", 1.0, 0.5, 2.0, 0.05).unit("x").name(K + "scale").section(K + "section.size");
        // a changed setting shows at once (the look is otherwise read again every 50 ms)
        for (dev.lumora.api.config.Setting<?> setting : s.all()) {
            setting.onChange(v -> WidgetDesign.changed());
        }
    }

    @Override
    public void onEnable(AddonContext context) {
        WidgetDesign.use(this::look);
    }

    @Override
    public void onDisable(AddonContext context) {
        WidgetDesign.use(null);
    }

    WidgetDesign.Look look() {
        return new WidgetDesign.Look(background.get(), backgroundColor.argb(), corners.get(), border.get(), borderColor.argb(),
                accentBar.get(), accentTheme.get() ? 0 : 0xFF000000 | accentColor.argb(),
                labelTheme.get() ? 0 : 0xFF000000 | labelColor.argb(), valueColor.argb(), shadow.get(), format.get(),
                uppercase.get(), (float) scale.get().doubleValue());
    }

    /** Lumora's own look (the defaults): night background, accent stripe, accent labels. */
    void lumora() {
        background.set(true);
        backgroundColor.set(BACKGROUND);
        corners.set(0);
        border.set(false);
        borderColor.set(BORDER);
        accentBar.set(true);
        accentTheme.set(true);
        format.select(WidgetDesign.SPACE);
        uppercase.set(false);
        shadow.set(true);
        labelTheme.set(true);
        valueColor.setChroma(false);
        valueColor.set(Colors.TEXT);
    }

    /** Only text with a shadow: no background, no stripe, "FPS: 60". */
    void minimal() {
        background.set(false);
        border.set(false);
        accentBar.set(false);
        format.select(WidgetDesign.COLON);
        shadow.set(true);
    }

    /** Rounded boxes with a thin border, no stripe. */
    void rounded() {
        background.set(true);
        backgroundColor.set(Colors.withAlpha(Colors.BACKGROUND, 0xB4));
        corners.set(4);
        border.set(true);
        borderColor.set(BORDER);
        accentBar.set(false);
        format.select(WidgetDesign.SPACE);
        shadow.set(false);
    }
}
