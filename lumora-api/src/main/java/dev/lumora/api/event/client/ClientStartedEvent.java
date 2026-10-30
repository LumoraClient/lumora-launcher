package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** The game finished loading. Posted once, after all addons were enabled. */
public record ClientStartedEvent() implements LumoraEvent {
}
