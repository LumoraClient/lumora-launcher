package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** End of every client tick (20 per second, also in menus). {@code tick} counts from client start. */
public record ClientTickEvent(long tick) implements LumoraEvent {
}
