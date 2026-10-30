package dev.lumora.api.config;

import java.util.List;

/**
 * One of a fixed list of option ids. Labels are translated with {@code nameKey + "." + option}; unknown stored
 * values are rejected (the default stays).
 */
public final class ChoiceSetting extends Setting<String> {
    private final List<String> options;

    public ChoiceSetting(String key, String defaultValue, List<String> options) {
        super(key, defaultValue);
        this.options = List.copyOf(options);
        if (this.options.isEmpty() || !this.options.contains(defaultValue)) {
            throw new IllegalArgumentException("default " + defaultValue + " is not one of " + options + " (" + key + ")");
        }
    }

    @Override
    public SettingType type() {
        return SettingType.CHOICE;
    }

    public List<String> options() {
        return options;
    }

    /** Translation key of an option label. */
    public String optionKey(String option) {
        return nameKey() + "." + option;
    }

    /** Selects the next option (wraps around). */
    public void cycle(int direction) {
        int i = options.indexOf(get());
        int n = options.size();
        set(options.get(((i + direction) % n + n) % n));
    }

    @Override
    protected String sanitize(String value) {
        return options.contains(value) ? value : null;
    }

    @Override
    public String encode() {
        return get();
    }

    @Override
    protected String parse(String raw) {
        return raw.trim();
    }
}
