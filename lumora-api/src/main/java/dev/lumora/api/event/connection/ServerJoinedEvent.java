package dev.lumora.api.event.connection;

import dev.lumora.api.connection.ServerConnection;
import dev.lumora.api.event.LumoraEvent;

/**
 * The world of a server was reached; {@code connectMillis} is the time from "connect" to the world. Posted on the
 * client thread, before {@link dev.lumora.api.event.client.WorldJoinEvent} or right after it depending on the version.
 *
 * @since 1.2.0
 */
public record ServerJoinedEvent(ServerConnection connection, long connectMillis) implements LumoraEvent {
}
