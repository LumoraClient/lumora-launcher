package dev.lumora.api.config;

/** Free text up to {@code maxLength} characters; control characters are removed. */
public final class TextSetting extends Setting<String> {
    private final int maxLength;

    public TextSetting(String key, String defaultValue, int maxLength) {
        super(key, defaultValue);
        if (maxLength < 1 || maxLength > 4096 || defaultValue.length() > maxLength) {
            throw new IllegalArgumentException("invalid max length for " + key + ": " + maxLength);
        }
        this.maxLength = maxLength;
    }

    @Override
    public SettingType type() {
        return SettingType.TEXT;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String sanitize(String value) {
        StringBuilder b = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && b.length() < maxLength; i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c)) {
                b.append(c);
            }
        }
        return b.toString();
    }

    @Override
    public String encode() {
        return get();
    }

    @Override
    protected String parse(String raw) {
        return raw;
    }

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public TextSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public TextSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public TextSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public TextSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public TextSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public TextSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public TextSetting bind(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        super.bind(getter, setter);
        return this;
    }
}
