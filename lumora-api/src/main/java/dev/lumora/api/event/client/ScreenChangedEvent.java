package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** A menu opened or closed. Names are the simple class names of the screens; an empty name means no screen. */
public record ScreenChangedEvent(String previous, String current) implements LumoraEvent {
}
