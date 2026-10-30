package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** The game is closing; addons are disabled right after this event. */
public record ClientStoppingEvent() implements LumoraEvent {
}
