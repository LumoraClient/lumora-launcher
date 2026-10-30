package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/** A widget was moved in the editor (posted once when the drag ends). x/y are 0..1 of the free screen space. */
public record WidgetMovedEvent(WidgetInfo widget, float x, float y) implements LumoraEvent {
}
