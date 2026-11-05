package dev.lumora.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One constant of an enum, shown as a drop-down list by default ({@link #selection()}: a row of buttons,
 * {@link #arrows()}: ‹ value ›). Option ids and stored values are the lower-case constant names; labels are
 * translated with {@code nameKey + "." + id}. Stored names that no longer exist are rejected (the default stays).
 *
 * @param <E> the enum
 * @since 1.3.0
 */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> implements OptionSetting {
    private final List<E> constants;
    private final List<String> ids;
    private SettingType presentation = SettingType.DROPDOWN;

    public EnumSetting(String key, E defaultValue) {
        super(key, defaultValue);
        List<E> c = new ArrayList<>();
        List<String> i = new ArrayList<>();
        for (E e : defaultValue.getDeclaringClass().getEnumConstants()) {
            c.add(e);
            i.add(id(e));
        }
        this.constants = Collections.unmodifiableList(c);
        this.ids = Collections.unmodifiableList(i);
    }

    private static String id(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public SettingType type() {
        return presentation;
    }

    /** Shows the constants as a row of buttons (for a few short options). */
    public EnumSetting<E> selection() {
        presentation = SettingType.SELECTION;
        return this;
    }

    /** Shows the constants as ‹ value › with arrows. */
    public EnumSetting<E> arrows() {
        presentation = SettingType.CHOICE;
        return this;
    }

    /** All constants in declaration order. */
    public List<E> constants() {
        return constants;
    }

    @Override
    public List<String> optionIds() {
        return ids;
    }

    @Override
    public String selectedId() {
        return id(get());
    }

    @Override
    public boolean select(String id) {
        int i = ids.indexOf(id);
        return i >= 0 && set(constants.get(i));
    }

    @Override
    public String optionKey(String id) {
        return nameKey() + "." + id;
    }

    @Override
    protected E sanitize(E value) {
        return value;
    }

    @Override
    public String encode() {
        return id(get());
    }

    @Override
    protected E parse(String raw) {
        String v = raw.trim();
        for (E e : constants) {
            if (e.name().equalsIgnoreCase(v)) {
                return e;
            }
        }
        return null;
    }

    // ---- covariant setters (the concrete type, so no casts are needed)

    @Override
    public EnumSetting<E> name(String translationKey) {
        super.name(translationKey);
        return this;
    }

    @Override
    public EnumSetting<E> description(String translationKey) {
        super.description(translationKey);
        return this;
    }

    @Override
    public EnumSetting<E> advanced() {
        super.advanced();
        return this;
    }

    @Override
    public EnumSetting<E> section(String translationKey) {
        super.section(translationKey);
        return this;
    }

    @Override
    public EnumSetting<E> visibleWhen(java.util.function.BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public EnumSetting<E> requires(BooleanSetting parent) {
        super.requires(parent);
        return this;
    }

    @Override
    public EnumSetting<E> bind(java.util.function.Supplier<E> getter, java.util.function.Consumer<E> setter) {
        super.bind(getter, setter);
        return this;
    }
}
