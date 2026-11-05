package dev.lumora.api.config;

import java.util.Objects;

/**
 * A button in the settings screen that runs an action on the client thread (e.g. "reset position", "open folder").
 * It has no value worth storing: Lumora never saves it. The button text is the translation of {@link #label} (by
 * default {@code nameKey + ".button"}); without a translation the setting name is used.
 *
 * @since 1.3.0
 */
public final class ButtonSetting extends Setting<Boolean> {
    private final Runnable action;
    private String labelKey;

    public ButtonSetting(String key, Runnable action) {
        super(key, Boolean.FALSE);
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public SettingType type() {
        return SettingType.BUTTON;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    /** Translation key of the text on the button. */
    public ButtonSetting label(String translationKey) {
        this.labelKey = translationKey;
        return this;
    }

    /** Translation key of the text on the button ({@code nameKey + ".button"} unless set). */
    public String labelKey() {
        return labelKey != null ? labelKey : nameKey() + ".button";
    }

    /** Runs the action (what a click on the button does). */
    public void press() {
        action.run();
    }

    @Override
    protected Boolean sanitize(Boolean value) {
        return Boolean.FALSE;
    }

    @Override
    public String encode() {
        return "";
    }

    @Override
    protected Boolean parse(String raw) {
        return Boolean.FALSE;
    }

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public ButtonSetting name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public ButtonSetting description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public ButtonSetting advanced() {
        super.advanced();
        return this;
    }

    @Override
    public ButtonSetting section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public ButtonSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ButtonSetting requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public ButtonSetting bind(java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        super.bind(getter, setter);
        return this;
    }
}
