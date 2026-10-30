package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/** A widget was switched on/off ({@code enabled}) or shown/hidden ({@code visible}). */
public record WidgetToggledEvent(WidgetInfo widget, boolean enabled, boolean visible) implements LumoraEvent {
}
