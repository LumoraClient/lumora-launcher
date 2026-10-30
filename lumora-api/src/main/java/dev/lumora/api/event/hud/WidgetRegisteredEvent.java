package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/** A widget was registered (and appears in the HUD editor). */
public record WidgetRegisteredEvent(WidgetInfo widget) implements LumoraEvent {
}
