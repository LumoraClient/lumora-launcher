package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;

/** A widget was removed (unregistered or its addon was disabled). Its stored layout is kept for later. */
public record WidgetUnregisteredEvent(String widgetId, String addonId) implements LumoraEvent {
}
