package dev.lumora.api.event.hud;

import dev.lumora.api.event.LumoraEvent;

/** Another HUD profile (Default, PvP, ...) became active. */
public record LayoutProfileChangedEvent(String previous, String current) implements LumoraEvent {
}
