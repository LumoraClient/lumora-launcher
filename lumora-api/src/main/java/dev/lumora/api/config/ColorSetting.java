package dev.lumora.api.config;

import dev.lumora.api.render.Colors;

/**
 * ARGB color, stored as {@code #AARRGGBB}. Without {@code alpha} the color is always opaque. With {@link #chroma()}
 * the color picker offers a rainbow ("chroma") that cycles through all hues at an adjustable speed; it is stored
 * as {@code #AARRGGBB chroma <speed>}. Draw with {@link #argb()}, which includes the rainbow; {@link #get()} is the
 * picked base color.
 */
public final class ColorSetting extends Setting<Integer> {
    private final boolean alpha;
    private boolean chromaAllowed;
    private boolean chroma;
    private int chromaSpeed = 5;

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

    /** Offers the rainbow ("chroma") in the color picker. @since 1.3.0 */
    public ColorSetting chroma() {
        chromaAllowed = true;
        return this;
    }

    /** Whether the color picker offers the rainbow. @since 1.3.0 */
    public boolean chromaAllowed() {
        return chromaAllowed;
    }

    /** Whether the rainbow is on. @since 1.3.0 */
    public boolean isChroma() {
        return chroma && chromaAllowed;
    }

    /** Speed of the rainbow, 1 (slow) to 10 (fast). @since 1.3.0 */
    public int chromaSpeed() {
        return chromaSpeed;
    }

    /** Switches the rainbow on or off (only if {@link #chroma()} offers it). @since 1.3.0 */
    public void setChroma(boolean on) {
        boolean v = on && chromaAllowed;
        if (v != chroma) {
            chroma = v;
            fireChanged();
        }
    }

    /** Sets the rainbow speed (clamped to 1..10). @since 1.3.0 */
    public void setChromaSpeed(int speed) {
        int v = Math.max(1, Math.min(10, speed));
        if (v != chromaSpeed) {
            chromaSpeed = v;
            fireChanged();
        }
    }

    /** The color to draw now: the rainbow while it is on (alpha of the base color), else {@link #get()}. */
    public int argb() {
        return argbAt(System.currentTimeMillis());
    }

    /** The color to draw at {@code millis} (wall clock), see {@link #argb()}. @since 1.3.0 */
    public int argbAt(long millis) {
        int base = get();
        if (!isChroma()) {
            return base;
        }
        // one full hue cycle takes 12 s at speed 1 and 1.2 s at speed 10
        float hue = (float) ((millis * chromaSpeed / 12000.0) % 1.0);
        return (base & 0xFF000000) | (hsv(hue) & 0xFFFFFF);
    }

    /** Fully saturated, bright color of the hue {@code h} (0..1). */
    static int hsv(float h) {
        float x = (h % 1f + 1f) % 1f * 6f;
        int i = (int) x;
        float f = x - i;
        int up = Math.round(255 * f);
        int down = 255 - up;
        int rgb = switch (i) {
            case 0 -> 0xFF0000 | up << 8;
            case 1 -> down << 16 | 0xFF00;
            case 2 -> 0xFF00 | up;
            case 3 -> down << 8 | 0xFF;
            case 4 -> up << 16 | 0xFF;
            default -> 0xFF0000 | down;
        };
        return 0xFF000000 | rgb;
    }

    @Override
    protected void onReset() {
        chroma = false;
        chromaSpeed = 5;
    }

    @Override
    protected Integer sanitize(Integer value) {
        return alpha ? value : value | 0xFF000000;
    }

    @Override
    public String encode() {
        String hex = Colors.toHex(get());
        return isChroma() ? hex + " chroma " + chromaSpeed : hex;
    }

    @Override
    protected Integer parse(String raw) {
        String[] parts = raw.trim().split("\\s+");
        Integer value = Colors.parseHex(parts[0]);
        if (value == null) {
            return null;
        }
        boolean on = false;
        int speed = 5;
        if (parts.length >= 2 && parts[1].equalsIgnoreCase("chroma")) {
            // kept even before chroma() was called: stored values are applied when the setting is added
            on = true;
            if (parts.length >= 3) {
                try {
                    speed = Math.max(1, Math.min(10, Integer.parseInt(parts[2])));
                } catch (NumberFormatException e) {
                    speed = 5;
                }
            }
        }
        chroma = on;
        chromaSpeed = speed;
        return value;
    }

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public ColorSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public ColorSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public ColorSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public ColorSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public ColorSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ColorSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public ColorSetting bind(java.util.function.Supplier<Integer> getter, java.util.function.Consumer<Integer> setter) {
        super.bind(getter, setter);
        return this;
    }
}
