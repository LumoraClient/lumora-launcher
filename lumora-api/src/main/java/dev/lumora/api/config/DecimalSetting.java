package dev.lumora.api.config;

import java.util.Locale;

/**
 * Decimal number in {@code [min, max]}, rounded to {@code step} (if &gt; 0); values outside are clamped, NaN and
 * infinity rejected. Shown as slider if {@code slider} is set.
 */
public final class DecimalSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final boolean slider;
    private String unit = "";

    public DecimalSetting(String key, double defaultValue, double min, double max, double step, boolean slider) {
        super(key, defaultValue);
        if (!(min <= max) || defaultValue < min || defaultValue > max || step < 0 || !Double.isFinite(step)) {
            throw new IllegalArgumentException("invalid range for " + key + ": " + min + ".." + max + " default " + defaultValue);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.slider = slider;
    }

    @Override
    public SettingType type() {
        return slider ? SettingType.SLIDER : SettingType.DECIMAL;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public double doubleValue() {
        return get();
    }

    public float floatValue() {
        return (float) (double) get();
    }

    /** Unit shown after the value, e.g. "×", "s" or " Blöcke". @since 1.3.0 */
    public DecimalSetting unit(String unit) {
        this.unit = unit == null ? "" : unit;
        return this;
    }

    public String unit() {
        return unit;
    }

    @Override
    protected Double sanitize(Double value) {
        double v = value;
        if (!Double.isFinite(v)) {
            return null;
        }
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            // avoid 0.30000000000000004 in the stored text
            v = Math.round(v * 1e6) / 1e6;
        }
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public String encode() {
        return String.format(Locale.ROOT, "%s", get());
    }

    @Override
    protected Double parse(String raw) {
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public DecimalSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public DecimalSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public DecimalSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public DecimalSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public DecimalSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public DecimalSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public DecimalSetting bind(java.util.function.Supplier<Double> getter, java.util.function.Consumer<Double> setter) {
        super.bind(getter, setter);
        return this;
    }
}
