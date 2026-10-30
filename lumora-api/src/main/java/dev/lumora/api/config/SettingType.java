package dev.lumora.api.config;

/** Kind of a setting; decides which control the Lumora settings screen shows. */
public enum SettingType {
    /** On/off switch ({@link BooleanSetting}). */
    BOOLEAN,
    /** Whole number input with +/- ({@link IntegerSetting}). */
    INTEGER,
    /** Decimal number input with +/- ({@link DecimalSetting}). */
    DECIMAL,
    /** Slider for an integer or decimal range ({@link IntegerSetting} / {@link DecimalSetting} with {@code slider}). */
    SLIDER,
    /** Free text ({@link TextSetting}). */
    TEXT,
    /** One of a fixed list of options ({@link ChoiceSetting}). */
    CHOICE,
    /** ARGB color ({@link ColorSetting}). */
    COLOR,
    /** Keyboard key ({@link KeybindSetting}). */
    KEYBIND
}
