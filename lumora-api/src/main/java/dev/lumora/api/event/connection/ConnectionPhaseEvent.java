package dev.lumora.api.event.connection;

import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.connection.ServerConnection;
import dev.lumora.api.event.LumoraEvent;

/**
 * The connection moved to another phase ({@code connection.phase()}); {@code previous} is where it was and
 * {@code previousMillis} how long that took. Posted on the client thread.
 *
 * @since 1.2.0
 */
public record ConnectionPhaseEvent(ServerConnection connection, ConnectionPhase previous, long previousMillis) implements LumoraEvent {
}
