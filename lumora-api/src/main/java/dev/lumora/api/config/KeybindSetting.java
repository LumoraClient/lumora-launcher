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
}
