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
    BooleanSetting rgb;
    BooleanSetting rgbAccent;
    BooleanSetting rgbBorder;
    BooleanSetting rgbLabel;
    BooleanSetting rgbValue;
    IntegerSetting rgbSpeed;
    IntegerSetting rgbSaturation;
    IntegerSetting rgbBrightness;
    BooleanSetting rgbWave;

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        s.button("preset_lumora", this::lumora).label(K + "apply").name(K + "preset_lumora").section(K + "section.presets");
        s.button("preset_minimal", this::minimal).label(K + "apply").name(K + "preset_minimal");
        s.button("preset_rounded", this::rounded).label(K + "apply").name(K + "preset_rounded");
        s.button("preset_rgb", this::rgbPreset).label(K + "apply").name(K + "preset_rgb");

        background = s.toggle("background", true).name(K + "background").section(K + "section.look");
        backgroundColor = s.color("background_color", BACKGROUND, true).name(K + "background_color").requires(background);
        corners = s.slider("corners", 0, 0, 6).unit(" px").name(K + "corners");
        border = s.toggle("border", false).name(K + "border");
        borderColor = s.color("border_color", BORDER, true).name(K + "border_color").requires(border);
        accentBar = s.toggle("accent_bar", true).name(K + "accent_bar");
        accentTheme = s.checkbox("accent_theme", true).name(K + "accent_theme").requires(accentBar);
        accentColor = s.color("accent_color", Colors.ACCENT, false).name(K + "accent_color")
                .visibleWhen(() -> accentBar.get() && !accentTheme.get());
        scale = s.slider("scale", 1.0, 0.5, 2.0, 0.05).unit("x").name(K + "scale");

        format = s.dropdown("format", WidgetDesign.SPACE, WidgetDesign.SPACE, WidgetDesign.COLON,
                WidgetDesign.BRACKETS, WidgetDesign.VALUE_FIRST, WidgetDesign.VALUE_ONLY).name(K + "format").section(K + "section.text");
        uppercase = s.toggle("uppercase", false).name(K + "uppercase");
        shadow = s.toggle("shadow", true).name(K + "shadow");

        labelTheme = s.toggle("label_theme", true).name(K + "label_theme").section(K + "section.colors");
        labelColor = s.color("label_color", Colors.ACCENT_HOVER, false).name(K + "label_color")
                .visibleWhen(() -> !labelTheme.get());
        valueColor = s.color("value_color", Colors.TEXT, false).name(K + "value_color");

        // RGB: the chosen parts run through the rainbow (the colour pickers above have no rainbow of their own)
        rgb = s.toggle("rgb", false).name(K + "rgb").section(K + "section.rgb");
        rgbAccent = s.checkbox("rgb_accent", true).name(K + "rgb_accent").requires(rgb);
        rgbBorder = s.checkbox("rgb_border", true).name(K + "rgb_border").requires(rgb);
        rgbLabel = s.checkbox("rgb_label", true).name(K + "rgb_label").requires(rgb);
        rgbValue = s.checkbox("rgb_value", false).name(K + "rgb_value").requires(rgb);
        rgbSpeed = s.slider("rgb_speed", 4, 1, 10).name(K + "rgb_speed").requires(rgb);
        rgbSaturation = s.slider("rgb_saturation", 85, 0, 100).step(5).unit(" %").name(K + "rgb_saturation").requires(rgb);
        rgbBrightness = s.slider("rgb_brightness", 100, 10, 100).step(5).unit(" %").name(K + "rgb_brightness").requires(rgb);
        rgbWave = s.toggle("rgb_wave", true).name(K + "rgb_wave").requires(rgb);
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
                uppercase.get(), (float) scale.get().doubleValue(),
                new WidgetDesign.Rgb(rgb.get(), rgbAccent.get(), rgbBorder.get(), rgbLabel.get(), rgbValue.get(), rgbSpeed.get(),
                        rgbSaturation.get(), rgbBrightness.get(), rgbWave.get()));
    }

    /** Lumora's own look (the defaults): night background, accent stripe, accent labels. */
    void lumora() {
        rgb.set(false);
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

    /** RGB: rounded boxes with a rainbow border, stripe and labels running across the widgets. */
    void rgbPreset() {
        background.set(true);
        backgroundColor.set(Colors.withAlpha(Colors.BACKGROUND, 0xB4));
        corners.set(3);
        border.set(true);
        borderColor.set(0xC0FFFFFF);
        accentBar.set(true);
        shadow.set(true);
        rgb.set(true);
        rgbAccent.set(true);
        rgbBorder.set(true);
        rgbLabel.set(true);
        rgbValue.set(false);
        rgbSpeed.set(4);
        rgbSaturation.set(85);
        rgbBrightness.set(100);
        rgbWave.set(true);
    }
}
