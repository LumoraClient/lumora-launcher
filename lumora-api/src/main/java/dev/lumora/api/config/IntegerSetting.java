package dev.lumora.api.config;

/** Whole number in {@code [min, max]}; values outside are clamped. Shown as slider if {@code slider} is set. */
public final class IntegerSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final boolean slider;

    public IntegerSetting(String key, int defaultValue, int min, int max, boolean slider) {
        super(key, defaultValue);
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("invalid range for " + key + ": " + min + ".." + max + " default " + defaultValue);
        }
        this.min = min;
        this.max = max;
        this.slider = slider;
    }

    @Override
    public SettingType type() {
        return slider ? SettingType.SLIDER : SettingType.INTEGER;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int intValue() {
        return get();
    }

    @Override
    protected Integer sanitize(Integer value) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String encode() {
        return Integer.toString(get());
    }

    @Override
    protected Integer parse(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
