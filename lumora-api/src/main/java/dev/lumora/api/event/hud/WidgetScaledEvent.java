package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/** A widget was resized in the editor (posted once when the resize ends). */
public record WidgetScaledEvent(WidgetInfo widget, float scale) implements LumoraEvent {
}
