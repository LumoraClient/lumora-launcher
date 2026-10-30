package dev.lumora.api.config;

/** On/off setting. */
public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, boolean defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public SettingType type() {
        return SettingType.BOOLEAN;
    }

    /** Shortcut for {@code get()} as primitive. */
    public boolean enabled() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    protected Boolean sanitize(Boolean value) {
        return value;
    }

    @Override
    public String encode() {
        return Boolean.toString(get());
    }

    @Override
    protected Boolean parse(String raw) {
        String v = raw.trim();
        if (v.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (v.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return null;
    }
}
