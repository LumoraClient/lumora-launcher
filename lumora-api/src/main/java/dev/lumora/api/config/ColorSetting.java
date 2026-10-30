package dev.lumora.api.config;

import dev.lumora.api.render.Colors;

/** ARGB color, stored as {@code #AARRGGBB}. Without {@code alpha} the color is always opaque. */
public final class ColorSetting extends Setting<Integer> {
    private final boolean alpha;

    public ColorSetting(String key, int defaultArgb, boolean alpha) {
        super(key, alpha ? defaultArgb : defaultArgb | 0xFF000000);
        this.alpha = alpha;
    }

    @Override
    public SettingType type() {
        return SettingType.COLOR;
    }

    public boolean hasAlpha() {
        return alpha;
    }

    public int argb() {
        return get();
    }

    @Override
    protected Integer sanitize(Integer value) {
        return alpha ? value : value | 0xFF000000;
    }

    @Override
    public String encode() {
        return Colors.toHex(get());
    }

    @Override
    protected Integer parse(String raw) {
        return Colors.parseHex(raw);
    }
}
