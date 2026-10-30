package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.hud.WidgetInfo;

/** Selection changed in the HUD editor; {@code widget} is {@code null} when nothing is selected. */
public record WidgetSelectedEvent(WidgetInfo widget) implements LumoraEvent {
}
