package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;

/** The local player entity was replaced (respawn) or moved to another dimension. */
public record PlayerChangedEvent(Reason reason, String dimension) implements LumoraEvent {
    public enum Reason { RESPAWN, DIMENSION }
}
