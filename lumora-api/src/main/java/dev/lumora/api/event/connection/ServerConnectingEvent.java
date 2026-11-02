package dev.lumora.api.event.connection;

import dev.lumora.api.connection.ServerConnection;
import dev.lumora.api.event.LumoraEvent;

/**
 * Minecraft started connecting to a server (server list, direct connect, Quick Play, transfer or reconnect).
 * {@code attempt} counts automatic reconnects (0 = first try). Posted on the client thread.
 *
 * @since 1.2.0
 */
public record ServerConnectingEvent(ServerConnection connection, int attempt) implements LumoraEvent {
}
