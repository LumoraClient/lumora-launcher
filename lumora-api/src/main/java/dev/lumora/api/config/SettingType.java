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
    KEYBIND,
    /** Check box, a compact on/off ({@link BooleanSetting#checkbox()}). @since 1.3.0 */
    CHECKBOX,
    /** One of a list of options in a drop-down list ({@link ChoiceSetting#dropdown()}, {@link EnumSetting}). @since 1.3.0 */
    DROPDOWN,
    /** One of a few options as a row of buttons ({@link ChoiceSetting#selection()}, {@link EnumSetting#selection()}). @since 1.3.0 */
    SELECTION,
    /** A button that runs an action, no stored value ({@link ButtonSetting}). @since 1.3.0 */
    BUTTON
}
