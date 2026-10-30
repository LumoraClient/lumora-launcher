package dev.lumora.core.hud;

import dev.lumora.api.event.hud.WidgetToggledEvent;
import dev.lumora.api.hud.HudWidget;
import dev.lumora.api.hud.WidgetHost;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.api.i18n.Translations;
import dev.lumora.core.config.SettingsBinder;

import java.util.Optional;

/** A registered widget with its current state (a live object of the active profile). Client thread. */
public final class WidgetEntry implements WidgetInfo, WidgetHost {
    final WidgetRegistryImpl registry;
    final String id;
    final String addonId;
    final HudWidget widget;
    final long seq;
    WidgetState state;
    SettingsBinder binder;
    /** {@code onEnable} was called and {@code onDisable} not yet. */
    boolean running;
    volatile String failure;
    final Bounds bounds = new Bounds();

    WidgetEntry(WidgetRegistryImpl registry, String addonId, HudWidget widget, long seq) {
        this.registry = registry;
        this.id = addonId + ":" + widget.id();
        this.addonId = addonId;
        this.widget = widget;
        this.seq = seq;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String fullId() {
        return id;
    }

    @Override
    public String addonId() {
        return addonId;
    }

    @Override
    public Translations translations() {
        return registry.translations();
    }

    @Override
    public HudWidget widget() {
        return widget;
    }

    @Override
    public String category() {
        try {
            return widget.category();
        } catch (RuntimeException e) {
            return "general";
        }
    }

    public WidgetState state() {
        return state;
    }

    public Bounds bounds() {
        return bounds;
    }

    @Override
    public boolean isEnabled() {
        return state.enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) {
            failure = null;
        }
        if (state.enabled == enabled) {
            return;
        }
        state.enabled = enabled;
        registry.syncRunning(this);
        registry.changed();
        registry.events().post(new WidgetToggledEvent(this, state.enabled, state.shown));
    }

    @Override
    public boolean isShown() {
        return state.shown;
    }

    @Override
    public void setShown(boolean shown) {
        if (state.shown == shown) {
            return;
        }
        state.shown = shown;
        registry.changed();
        registry.events().post(new WidgetToggledEvent(this, state.enabled, state.shown));
    }

    @Override
    public float x() {
        return state.x;
    }

    @Override
    public float y() {
        return state.y;
    }

    @Override
    public float scale() {
        return state.scale;
    }

    @Override
    public int layer() {
        return state.layer;
    }

    @Override
    public Optional<String> failure() {
        return Optional.ofNullable(failure);
    }

    /** Draws in game: enabled, shown, not failed. */
    public boolean active() {
        return state.enabled && state.shown && failure == null && running;
    }

    @Override
    public String toString() {
        return id;
    }
}
