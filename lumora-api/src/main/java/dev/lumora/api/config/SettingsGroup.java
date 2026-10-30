package dev.lumora.api.config;

import dev.lumora.api.Registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Ordered set of settings of one widget or addon. Labels default to {@code prefix + key}, e.g.
 * {@code session-timer.widget.timer.show_seconds}. Keys must be unique within the group.
 */
public final class SettingsGroup implements Iterable<Setting<?>> {
    private final String translationPrefix;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final List<Consumer<Setting<?>>> addListeners = new CopyOnWriteArrayList<>();

    /** @param translationPrefix prefix of the label keys, normally ending with a dot */
    public SettingsGroup(String translationPrefix) {
        this.translationPrefix = Objects.requireNonNull(translationPrefix);
    }

    public String translationPrefix() {
        return translationPrefix;
    }

    /** Adds a setting; throws {@link IllegalArgumentException} if the key is taken. */
    public synchronized <S extends Setting<?>> S add(S setting) {
        if (settings.containsKey(setting.key())) {
            throw new IllegalArgumentException("duplicate setting key: " + setting.key());
        }
        setting.defaultName(translationPrefix + setting.key());
        settings.put(setting.key(), setting);
        for (Consumer<Setting<?>> l : addListeners) {
            l.accept(setting);
        }
        return setting;
    }

    public BooleanSetting toggle(String key, boolean defaultValue) {
        return add(new BooleanSetting(key, defaultValue));
    }

    public IntegerSetting integer(String key, int defaultValue, int min, int max) {
        return add(new IntegerSetting(key, defaultValue, min, max, false));
    }

    public IntegerSetting slider(String key, int defaultValue, int min, int max) {
        return add(new IntegerSetting(key, defaultValue, min, max, true));
    }

    public DecimalSetting decimal(String key, double defaultValue, double min, double max, double step) {
        return add(new DecimalSetting(key, defaultValue, min, max, step, false));
    }

    public DecimalSetting slider(String key, double defaultValue, double min, double max, double step) {
        return add(new DecimalSetting(key, defaultValue, min, max, step, true));
    }

    public TextSetting text(String key, String defaultValue, int maxLength) {
        return add(new TextSetting(key, defaultValue, maxLength));
    }

    public ChoiceSetting choice(String key, String defaultValue, String... options) {
        return add(new ChoiceSetting(key, defaultValue, List.of(options)));
    }

    public ColorSetting color(String key, int defaultArgb, boolean alpha) {
        return add(new ColorSetting(key, defaultArgb, alpha));
    }

    public KeybindSetting keybind(String key, int defaultKey) {
        return add(new KeybindSetting(key, defaultKey));
    }

    public synchronized Optional<Setting<?>> get(String key) {
        return Optional.ofNullable(settings.get(key));
    }

    /** Snapshot in definition order. */
    public synchronized List<Setting<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(settings.values()));
    }

    public synchronized boolean isEmpty() {
        return settings.isEmpty();
    }

    @Override
    public Iterator<Setting<?>> iterator() {
        return all().iterator();
    }

    /** Resets every setting to its default. */
    public void resetAll() {
        for (Setting<?> s : all()) {
            s.reset();
        }
    }

    /**
     * Internal hook for the Lumora core: called for every setting added from now on (to apply stored values and
     * watch changes).
     */
    public Registration onAdd(Consumer<Setting<?>> listener) {
        addListeners.add(listener);
        return new Registration() {
            @Override
            public void unregister() {
                addListeners.remove(listener);
            }

            @Override
            public boolean isActive() {
                return addListeners.contains(listener);
            }
        };
    }
}
