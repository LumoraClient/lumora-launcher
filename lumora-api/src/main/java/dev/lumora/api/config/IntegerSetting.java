package dev.lumora.api.config;

/** Whole number in {@code [min, max]}; values outside are clamped. Shown as slider if {@code slider} is set. */
public final class IntegerSetting extends Setting<Integer> {
    private final int min;
    private final int max;
    private final boolean slider;
    private int step = 1;
    private String unit = "";

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

    /** Only multiples of {@code step} (counted from {@code min}) are accepted, e.g. 5 for 0, 5, 10 … @since 1.3.0 */
    public IntegerSetting step(int step) {
        if (step < 1) {
            throw new IllegalArgumentException("step must be at least 1: " + key());
        }
        this.step = step;
        return this;
    }

    public int step() {
        return step;
    }

    /** Unit shown after the value, e.g. "%", "ms" or " px". @since 1.3.0 */
    public IntegerSetting unit(String unit) {
        this.unit = unit == null ? "" : unit;
        return this;
    }

    public String unit() {
        return unit;
    }

    @Override
    protected Integer sanitize(Integer value) {
        int v = Math.max(min, Math.min(max, value));
        if (step > 1) {
            v = min + Math.round((v - min) / (float) step) * step;
            if (v > max) {
                v -= step;
            }
        }
        return v;
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

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public IntegerSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public IntegerSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public IntegerSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public IntegerSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public IntegerSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public IntegerSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public IntegerSetting bind(java.util.function.Supplier<Integer> getter, java.util.function.Consumer<Integer> setter) {
        super.bind(getter, setter);
        return this;
    }
}
