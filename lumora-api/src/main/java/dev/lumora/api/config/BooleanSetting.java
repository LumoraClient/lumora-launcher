package dev.lumora.api.config;

/** On/off setting: a switch, or a check box with {@link #checkbox()}. */
public final class BooleanSetting extends Setting<Boolean> {
    private boolean checkbox;

    public BooleanSetting(String key, boolean defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public SettingType type() {
        return checkbox ? SettingType.CHECKBOX : SettingType.BOOLEAN;
    }

    /** Shows a check box instead of a switch (for lists of options). @since 1.3.0 */
    public BooleanSetting checkbox() {
        checkbox = true;
        return this;
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

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public BooleanSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public BooleanSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public BooleanSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public BooleanSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public BooleanSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public BooleanSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public BooleanSetting bind(java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        super.bind(getter, setter);
        return this;
    }
}
