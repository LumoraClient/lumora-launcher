package dev.lumora.core.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stored state of one widget in one profile. Unknown widgets keep their state (their addon may come back). */
public final class WidgetState {
    public boolean enabled;
    /** Hidden widgets stay enabled but are not drawn. */
    public boolean shown = true;
    /** 0..1 of the free screen space. */
    public float x;
    public float y;
    public float scale = 1f;
    public int layer;
    /** Encoded values of the widget's own (not bound) settings. */
    public final Map<String, String> settings = new LinkedHashMap<>();

    public WidgetState copy() {
        WidgetState s = new WidgetState();
        s.copyFrom(this);
        return s;
    }

    public void copyFrom(WidgetState o) {
        enabled = o.enabled;
        shown = o.shown;
        x = o.x;
        y = o.y;
        scale = o.scale;
        layer = o.layer;
        settings.clear();
        settings.putAll(o.settings);
    }

    /** Value equality (used by the editor's undo history). */
    public boolean sameAs(WidgetState o) {
        return enabled == o.enabled && shown == o.shown && x == o.x && y == o.y && scale == o.scale && layer == o.layer
                && settings.equals(o.settings);
    }
}
