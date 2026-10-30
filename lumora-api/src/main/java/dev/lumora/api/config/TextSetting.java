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
}
