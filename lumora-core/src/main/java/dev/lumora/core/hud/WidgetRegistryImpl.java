package dev.lumora.core.hud;

import dev.lumora.api.Registration;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.config.SettingChangedEvent;
import dev.lumora.api.event.hud.LayoutProfileChangedEvent;
import dev.lumora.api.event.hud.LayoutSavedEvent;
import dev.lumora.api.event.hud.WidgetRegisteredEvent;
import dev.lumora.api.event.hud.WidgetUnregisteredEvent;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.hud.WidgetRegistry;
import dev.lumora.api.i18n.Translations;
import dev.lumora.api.notify.Notifications;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.config.SettingsBinder;
import dev.lumora.core.event.SimpleEventBus;
import dev.lumora.core.io.DebouncedFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Widget registry and the HUD layout it is stored in. Lives on the client thread. The render order is cached and
 * only rebuilt when widgets are added/removed or layers change, never per frame.
 */
public final class WidgetRegistryImpl {
    private static final Comparator<WidgetEntry> ORDER = Comparator.<WidgetEntry>comparingInt(e -> e.state.layer).thenComparingLong(e -> e.seq);

    private final SimpleEventBus events;
    private final Translations translations;
    private final CoreLogger log;
    private final Notifications notifications;
    private final LongSupplier ticks;
    private final Executor clientThread;
    private final Map<String, WidgetEntry> byId = new LinkedHashMap<>();
    private volatile List<WidgetEntry> order = List.of();
    private final HudLayout layout;
    private final DebouncedFile file;
    private long seq;

    public WidgetRegistryImpl(SimpleEventBus events, Translations translations, CoreLogger log, Notifications notifications,
                              Path file, Path legacyFile, Executor io, Executor clientThread, LongSupplier ticks) {
        this.events = events;
        this.translations = translations;
        this.log = log;
        this.notifications = notifications;
        this.ticks = ticks;
        this.clientThread = clientThread;
        this.layout = LayoutStore.load(file, legacyFile, log);
        this.file = new DebouncedFile(file, () -> LayoutStore.toJson(layout), io, log);
        // tell listeners on the client thread that the file is written
        this.file.afterWrite(() -> clientThread.execute(() -> events.post(new LayoutSavedEvent(layout.activeProfile))));
        if (legacyFile != null && !java.nio.file.Files.exists(file)) {
            this.file.markDirty(0);
        }
    }

    SimpleEventBus events() {
        return events;
    }

    Translations translations() {
        return translations;
    }

    public HudLayout layout() {
        return layout;
    }

    /** Marks the layout as changed; it is written a moment later. */
    public void changed() {
        file.markDirty(ticks.getAsLong());
    }

    public void saveSoon() {
        file.saveAsync();
    }

    public void saveNow() {
        file.saveNow();
    }

    public boolean dirty() {
        return file.dirty();
    }

    /** Registers on behalf of {@code addonId}. */
    public Registration register(String addonId, HudWidget widget) {
        if (widget.isAttached()) {
            throw new IllegalArgumentException("widget " + widget.fullId() + " is already registered");
        }
        String id = addonId + ":" + widget.id();
        if (byId.containsKey(id)) {
            throw new IllegalArgumentException("duplicate widget id: " + id);
        }
        WidgetEntry e = new WidgetEntry(this, addonId, widget, ++seq);
        e.state = stateFor(e);
        SettingsGroup settings = new SettingsGroup(addonId + ".widget." + widget.id() + ".");
        widget.attach(e, settings);
        try {
            widget.configure(settings);
        } catch (RuntimeException | LinkageError ex) {
            throw new IllegalArgumentException("widget " + id + " failed to define its settings: " + ex, ex);
        }
        e.binder = new SettingsBinder(settings, () -> e.state.settings, s -> {
            changed();
            events.post(new SettingChangedEvent(id, s));
        });
        try {
            widget.initialize();
        } catch (RuntimeException | LinkageError ex) {
            fail(e, "initialize", ex);
        }
        byId.put(id, e);
        rebuildOrder();
        events.post(new WidgetRegisteredEvent(e));
        syncRunning(e);
        return RegistrationTracker.of(() -> unregister(e));
    }

    private WidgetState stateFor(WidgetEntry e) {
        HudProfile p = layout.active();
        WidgetState s = p.widgets.get(e.id);
        if (s == null) {
            s = defaults(e.widget);
            p.widgets.put(e.id, s);
            changed();
        }
        return s;
    }

    static WidgetState defaults(HudWidget w) {
        WidgetState s = new WidgetState();
        WidgetPlacement d;
        try {
            d = w.defaultPlacement();
        } catch (RuntimeException ex) {
            d = WidgetPlacement.at(0.01f, 0.01f);
        }
        s.enabled = d.enabled();
        s.x = d.x();
        s.y = d.y();
        s.scale = clampScale(w, d.scale());
        s.layer = w.defaultLayer();
        return s;
    }

    static float clampScale(HudWidget w, float scale) {
        float min = 0.25f;
        float max = 4f;
        try {
            min = Math.max(0.25f, w.minScale());
            max = Math.min(4f, Math.max(min, w.maxScale()));
        } catch (RuntimeException ignored) {
            // defaults
        }
        return Math.max(min, Math.min(max, scale));
    }

    private void unregister(WidgetEntry e) {
        if (byId.get(e.id) != e) {
            return;
        }
        if (e.running) {
            e.running = false;
            call(e, "onDisable", e.widget::onDisable);
        }
        byId.remove(e.id);
        rebuildOrder();
        e.binder.close();
        call(e, "dispose", e.widget::dispose);
        events.post(new WidgetUnregisteredEvent(e.id, e.addonId));
    }

    /** Calls onEnable/onDisable so that the widget's running state matches enabled-and-not-failed. */
    void syncRunning(WidgetEntry e) {
        boolean want = e.state.enabled && e.failure == null;
        if (want && !e.running) {
            e.running = true;
            call(e, "onEnable", e.widget::onEnable);
        } else if (!want && e.running) {
            e.running = false;
            call(e, "onDisable", e.widget::onDisable);
        }
    }

    private void call(WidgetEntry e, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException | LinkageError ex) {
            if (!what.equals("onDisable") && !what.equals("dispose")) {
                fail(e, what, ex);
            } else {
                log.warn("Widget " + e.id + " failed in " + what, ex);
            }
        }
    }

    /** Switches a misbehaving widget off for this session (its stored state stays, re-enabling retries). */
    public void fail(WidgetEntry e, String where, Throwable error) {
        if (e.failure != null) {
            return;
        }
        e.failure = where + ": " + error;
        log.error("Widget " + e.id + " failed in " + where + " and was switched off", error);
        if (e.running) {
            e.running = false;
            try {
                e.widget.onDisable();
            } catch (RuntimeException | LinkageError ignored) {
                // already failing
            }
        }
        notifications.error(translations.translateOr("lumora.widget.failed.title", "Widget switched off"),
                translations.translateOr(e.widget.nameKey(), e.id) + ": " + error.getClass().getSimpleName());
    }

    public void rebuildOrder() {
        List<WidgetEntry> list = new ArrayList<>(byId.values());
        list.sort(ORDER);
        order = List.copyOf(list);
    }

    /** Render order (lowest layer first). Cached list, safe to iterate every frame. */
    public List<WidgetEntry> entries() {
        return order;
    }

    public Optional<WidgetEntry> entry(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Client thread, every tick. */
    public void tick() {
        List<WidgetEntry> list = order;
        for (int i = 0; i < list.size(); i++) {
            WidgetEntry e = list.get(i);
            if (e.running) {
                try {
                    e.widget.tick();
                } catch (RuntimeException | LinkageError ex) {
                    fail(e, "tick", ex);
                }
            }
        }
        file.tick(ticks.getAsLong());
    }

    // --- profiles

    public String activeProfile() {
        return layout.activeProfile;
    }

    /** Switches profile (created as a copy of the current one if new) and applies it to all widgets. */
    public boolean activateProfile(String profile) {
        if (!HudLayout.validProfileId(profile)) {
            return false;
        }
        String previous = layout.activeProfile;
        if (previous.equals(profile)) {
            return true;
        }
        if (!layout.profiles.containsKey(profile)) {
            layout.profiles.put(profile, layout.active().copy());
        }
        layout.activeProfile = profile;
        reloadStates();
        changed();
        events.post(new LayoutProfileChangedEvent(previous, profile));
        return true;
    }

    /** Deletes an own profile (built-in ones are only reset). */
    public boolean deleteProfile(String profile) {
        if (!layout.profiles.containsKey(profile)) {
            return false;
        }
        if (HudLayout.BUILT_IN.contains(profile)) {
            layout.profiles.get(profile).widgets.clear();
        } else {
            layout.profiles.remove(profile);
        }
        if (layout.activeProfile.equals(profile) && !layout.profiles.containsKey(profile)) {
            layout.activeProfile = HudLayout.DEFAULT;
        }
        reloadStates();
        changed();
        return true;
    }

    /** Re-reads every widget's state from the active profile (after a switch, undo or revert). */
    public void reloadStates() {
        for (WidgetEntry e : byId.values()) {
            e.state = stateFor(e);
            e.binder.reapply();
            syncRunning(e);
        }
        rebuildOrder();
    }

    /** Resets one widget to its defaults (position, scale, layer, on/off and settings). */
    public void resetToDefaults(WidgetEntry e) {
        WidgetState d = defaults(e.widget);
        e.state.copyFrom(d);
        e.binder.reapply();
        syncRunning(e);
        rebuildOrder();
        changed();
    }

    /** The public view for one addon; registrations are tracked. */
    public WidgetRegistry scoped(String addonId, RegistrationTracker tracker, Runnable guard) {
        return new WidgetRegistry() {
            @Override
            public Registration register(HudWidget widget) {
                guard.run();
                return tracker.track(WidgetRegistryImpl.this.register(addonId, widget));
            }

            @Override
            public List<WidgetInfo> all() {
                return WidgetRegistryImpl.this.all();
            }

            @Override
            public List<WidgetInfo> byCategory(String category) {
                return WidgetRegistryImpl.this.byCategory(category);
            }

            @Override
            public Optional<WidgetInfo> find(String fullId) {
                return Optional.ofNullable(byId.get(fullId));
            }
        };
    }

    public List<WidgetInfo> all() {
        return List.copyOf(order);
    }

    public List<WidgetInfo> byCategory(String category) {
        List<WidgetInfo> out = new ArrayList<>();
        for (WidgetEntry e : order) {
            if (e.category().equals(category)) {
                out.add(e);
            }
        }
        return out;
    }

    Executor clientThread() {
        return clientThread;
    }
}
