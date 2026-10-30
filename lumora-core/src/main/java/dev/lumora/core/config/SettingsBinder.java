package dev.lumora.core.config;

import dev.lumora.api.Registration;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Connects a {@link SettingsGroup} to a map of stored text values: stored values are applied when a setting is
 * added (and again via {@link #reapply()}, e.g. after a profile switch), every change of a not-bound setting is
 * written back. Bound settings are never stored here (their owner keeps them).
 */
public final class SettingsBinder {
    private final SettingsGroup group;
    private final Supplier<Map<String, String>> store;
    private final Consumer<Setting<?>> changed;
    private final List<Registration> registrations = new ArrayList<>();
    /** Set while values are applied from storage, so this does not count as a user change. */
    private boolean applying;

    /**
     * @param store   current storage map (may change, e.g. per profile)
     * @param changed called after a user change (also for bound settings), after storage was updated
     */
    public SettingsBinder(SettingsGroup group, Supplier<Map<String, String>> store, Consumer<Setting<?>> changed) {
        this.group = group;
        this.store = store;
        this.changed = changed;
        registrations.add(group.onAdd(this::attach));
        for (Setting<?> s : group.all()) {
            attach(s);
        }
    }

    private void attach(Setting<?> s) {
        apply(s);
        registrations.add(s.onChange(v -> {
            if (applying) {
                return;
            }
            if (!s.isBound()) {
                store.get().put(s.key(), s.encode());
            }
            changed.accept(s);
        }));
    }

    private void apply(Setting<?> s) {
        if (s.isBound()) {
            return;
        }
        applying = true;
        try {
            String raw = store.get().get(s.key());
            if (raw == null || !s.decode(raw)) {
                s.reset();
            }
        } finally {
            applying = false;
        }
    }

    /** Applies the stored values again (storage map changed). Settings without a stored value get their default. */
    public void reapply() {
        for (Setting<?> s : group.all()) {
            apply(s);
        }
    }

    public void close() {
        for (Registration r : registrations) {
            r.unregister();
        }
        registrations.clear();
    }
}
