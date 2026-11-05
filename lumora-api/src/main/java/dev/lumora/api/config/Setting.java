package dev.lumora.api.config;

import dev.lumora.api.Registration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One typed, validated setting. The description (key, type, range, default) is separate from the controls that
 * show it, so the same setting works in every Lumora settings screen.
 * <p>
 * By default Lumora stores the value (per widget and layout profile, or per addon). {@link #bind} instead reads and
 * writes an existing field of the owner (for example an existing config object); the owner then saves it itself
 * and Lumora does not store a second copy. Values are always validated by {@link #sanitize} first: out-of-range
 * numbers are clamped, invalid values are rejected.
 * <p>
 * Settings are meant to be used on the client thread.
 *
 * @param <T> value type
 */
public abstract class Setting<T> {
    private final String key;
    private final T defaultValue;
    private T value;
    private String nameKey;
    private String descriptionKey;
    private boolean advanced;
    private String sectionKey;
    private BooleanSupplier visibleWhen;
    private Supplier<T> getter;
    private Consumer<T> setter;
    private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();

    protected Setting(String key, T defaultValue) {
        if (key == null || !key.matches("[a-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("invalid setting key: " + key);
        }
        this.key = key;
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    public final String key() {
        return key;
    }

    public abstract SettingType type();

    public final T defaultValue() {
        return defaultValue;
    }

    /** Current value (from the binding, if bound). */
    public final T get() {
        if (getter != null) {
            T v = getter.get();
            return v != null ? v : defaultValue;
        }
        return value;
    }

    /**
     * Validates and stores the value, then notifies listeners. Returns {@code false} if the value was rejected or
     * did not change.
     */
    public final boolean set(T newValue) {
        if (newValue == null) {
            return false;
        }
        T v = sanitize(newValue);
        if (v == null || Objects.equals(v, get())) {
            return false;
        }
        if (setter != null) {
            setter.accept(v);
        } else {
            value = v;
        }
        for (Consumer<? super T> l : listeners) {
            l.accept(v);
        }
        return true;
    }

    public final void reset() {
        onReset();
        set(defaultValue);
    }

    /** Called by {@link #reset()} before the default is set (e.g. extra state like the chroma of a colour). @since 1.3.0 */
    protected void onReset() {
    }

    /**
     * Notifies the listeners with the current value although {@link #set} was not called (extra state changed, e.g.
     * the chroma of a colour), so Lumora stores it. @since 1.3.0
     */
    protected final void fireChanged() {
        T v = get();
        for (Consumer<? super T> l : listeners) {
            l.accept(v);
        }
    }

    /** Whether Lumora stores the value (false for buttons, which only run an action). @since 1.3.0 */
    public boolean isPersistent() {
        return true;
    }

    /** Clamps/normalises a value; returns {@code null} to reject it. */
    protected abstract T sanitize(T value);

    /** Current value as text (for storage). */
    public abstract String encode();

    /** Parses stored text; {@code null} if invalid. */
    protected abstract T parse(String raw);

    /** Applies a stored text value; returns {@code false} if it was invalid (the current value stays). */
    public final boolean decode(String raw) {
        T v = raw == null ? null : parse(raw);
        if (v == null || sanitize(v) == null) {
            return false;
        }
        set(v);
        return true;
    }

    /**
     * Reads and writes the value through the owner instead of Lumora's storage. The setter receives already
     * validated values; persist them yourself (debounced, not on every call).
     */
    public Setting<T> bind(Supplier<T> getter, Consumer<T> setter) {
        this.getter = Objects.requireNonNull(getter, "getter");
        this.setter = Objects.requireNonNull(setter, "setter");
        return this;
    }

    /** {@code true} if the value lives in the owner ({@link #bind}); Lumora then does not store it. */
    public final boolean isBound() {
        return getter != null;
    }

    /** Translation key of the label (default: group prefix + key). */
    public final String nameKey() {
        return nameKey;
    }

    /** Optional translation key of the help text (default: name key + ".description", shown if it exists). */
    public final String descriptionKey() {
        return descriptionKey != null ? descriptionKey : nameKey + ".description";
    }

    public Setting<T> name(String translationKey) {
        this.nameKey = translationKey;
        return this;
    }

    public Setting<T> description(String translationKey) {
        this.descriptionKey = translationKey;
        return this;
    }

    /**
     * Marks a rarely used setting. Since Lumora 0.3.13 the settings screen shows every setting, the mark no longer
     * hides it; kept so existing addons compile unchanged.
     */
    public Setting<T> advanced() {
        this.advanced = true;
        return this;
    }

    public final boolean isAdvanced() {
        return advanced;
    }

    /**
     * Starts a new section above this setting: a heading with the label of {@code translationKey} (like a divider
     * in the settings screen). @since 1.3.0
     */
    public Setting<T> section(String translationKey) {
        this.sectionKey = translationKey;
        return this;
    }

    /** Translation key of the section heading above this setting, or null. @since 1.3.0 */
    public final String sectionKey() {
        return sectionKey;
    }

    /**
     * Shows the setting only while {@code condition} is true, e.g. a colour only while its switch is on. Evaluated
     * on the client thread every time the settings screen is drawn. @since 1.3.0
     */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibleWhen = condition;
        return this;
    }

    /** Shows the setting only while {@code parent} is on. @since 1.3.0 */
    public Setting<T> requires(BooleanSetting parent) {
        Objects.requireNonNull(parent, "parent");
        return visibleWhen(parent::enabled);
    }

    /** Whether the settings screen shows this setting now ({@link #visibleWhen}); a failing condition shows it. @since 1.3.0 */
    public final boolean isVisible() {
        BooleanSupplier c = visibleWhen;
        if (c == null) {
            return true;
        }
        try {
            return c.getAsBoolean();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Called after every accepted change with the new value. */
    public final Registration onChange(Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return new Registration() {
            @Override
            public void unregister() {
                listeners.remove(listener);
            }

            @Override
            public boolean isActive() {
                return listeners.contains(listener);
            }
        };
    }

    final void defaultName(String translationKey) {
        if (nameKey == null) {
            nameKey = translationKey;
        }
    }

    @Override
    public String toString() {
        return key + "=" + encode();
    }
}
