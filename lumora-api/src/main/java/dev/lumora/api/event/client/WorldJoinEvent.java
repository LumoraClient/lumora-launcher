package dev.lumora.api.event.client;

import dev.lumora.api.event.LumoraEvent;
import dev.lumora.api.game.ConnectionInfo;

/** Joined a world (singleplayer or server). Posted on the client thread once the player exists. */
public record WorldJoinEvent(ConnectionInfo connection) implements LumoraEvent {
}
