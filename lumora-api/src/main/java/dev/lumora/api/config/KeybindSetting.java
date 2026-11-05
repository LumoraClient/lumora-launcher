package dev.lumora.api.config;

import dev.lumora.api.input.Keys;

/**
 * A keyboard key (GLFW key code, {@link Keys#UNBOUND} = none). This only stores the choice; to react to a key use
 * {@link dev.lumora.api.input.KeybindRegistry}, whose keybinds also appear in the settings.
 */
public final class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String key, int defaultKey) {
        super(key, defaultKey);
    }

    @Override
    public SettingType type() {
        return SettingType.KEYBIND;
    }

    public int keyCode() {
        return get();
    }

    @Override
    protected Integer sanitize(Integer value) {
        return Keys.isValid(value) ? value : null;
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
    public KeybindSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public KeybindSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public KeybindSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public KeybindSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public KeybindSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public KeybindSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public KeybindSetting bind(java.util.function.Supplier<Integer> getter, java.util.function.Consumer<Integer> setter) {
        super.bind(getter, setter);
        return this;
    }
}
