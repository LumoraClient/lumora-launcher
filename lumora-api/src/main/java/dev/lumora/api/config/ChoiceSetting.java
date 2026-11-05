package dev.lumora.api.config;

import java.util.List;

/**
 * One of a fixed list of option ids. Labels are translated with {@code nameKey + "." + option}; unknown stored
 * values are rejected (the default stays). The settings screen shows arrows (‹ value ›) by default,
 * {@link #dropdown()} a drop-down list and {@link #selection()} a row of buttons.
 */
public final class ChoiceSetting extends Setting<String> implements OptionSetting {
    private final List<String> options;
    private SettingType presentation = SettingType.CHOICE;

    public ChoiceSetting(String key, String defaultValue, List<String> options) {
        super(key, defaultValue);
        this.options = List.copyOf(options);
        if (this.options.isEmpty() || !this.options.contains(defaultValue)) {
            throw new IllegalArgumentException("default " + defaultValue + " is not one of " + options + " (" + key + ")");
        }
    }

    @Override
    public SettingType type() {
        return presentation;
    }

    /** Shows the options in a drop-down list. @since 1.3.0 */
    public ChoiceSetting dropdown() {
        presentation = SettingType.DROPDOWN;
        return this;
    }

    /** Shows the options as a row of buttons (for a few short options). @since 1.3.0 */
    public ChoiceSetting selection() {
        presentation = SettingType.SELECTION;
        return this;
    }

    public List<String> options() {
        return options;
    }

    /** Translation key of an option label. */
    @Override
    public String optionKey(String option) {
        return nameKey() + "." + option;
    }

    /** Selects the next option (wraps around). */
    @Override
    public void cycle(int direction) {
        int i = options.indexOf(get());
        int n = options.size();
        set(options.get(((i + direction) % n + n) % n));
    }

    @Override
    public List<String> optionIds() {
        return options;
    }

    @Override
    public String selectedId() {
        return get();
    }

    @Override
    public boolean select(String id) {
        return set(id);
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

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public ChoiceSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public ChoiceSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public ChoiceSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public ChoiceSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public ChoiceSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ChoiceSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public ChoiceSetting bind(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        super.bind(getter, setter);
        return this;
    }
}
