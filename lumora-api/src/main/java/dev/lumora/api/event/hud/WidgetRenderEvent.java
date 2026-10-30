package dev.lumora.api.event.hud;

import dev.lumora.api.event.Cancellable;
import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/**
 * Posted before a widget is drawn in game; cancel it to skip the widget for this frame. The same instance is
 * reused for every widget and frame: do not keep a reference. Only posted when someone listens.
 */
public final class WidgetRenderEvent implements LumoraEvent, Cancellable {
    private WidgetInfo widget;
    private boolean cancelled;

    /** Internal: set by the renderer before posting. */
    public WidgetRenderEvent set(WidgetInfo widget) {
        this.widget = widget;
        this.cancelled = false;
        return this;
    }

    public WidgetInfo widget() {
        return widget;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
