package dev.lumora.core.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/** One HUD layout profile: the state of every widget by global id. */
public final class HudProfile {
    public final Map<String, WidgetState> widgets = new LinkedHashMap<>();

    public HudProfile copy() {
        HudProfile p = new HudProfile();
        for (Map.Entry<String, WidgetState> e : widgets.entrySet()) {
            p.widgets.put(e.getKey(), e.getValue().copy());
        }
        return p;
    }

    /** Overwrites this profile with {@code other} while keeping the existing state objects (they are referenced). */
    public void restore(HudProfile other) {
        widgets.keySet().removeIf(id -> !other.widgets.containsKey(id));
        for (Map.Entry<String, WidgetState> e : other.widgets.entrySet()) {
            WidgetState mine = widgets.get(e.getKey());
            if (mine == null) {
                widgets.put(e.getKey(), e.getValue().copy());
            } else {
                mine.copyFrom(e.getValue());
            }
        }
    }

    public boolean sameAs(HudProfile o) {
        if (!widgets.keySet().equals(o.widgets.keySet())) {
            return false;
        }
        for (Map.Entry<String, WidgetState> e : widgets.entrySet()) {
            if (!e.getValue().sameAs(o.widgets.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
