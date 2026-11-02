package dev.lumora.api.event.connection;

import dev.lumora.api.connection.ConnectionPhase;
import dev.lumora.api.connection.DisconnectKind;
import dev.lumora.api.connection.ServerConnection;
import dev.lumora.api.event.LumoraEvent;

/**
 * A server connection ended or failed.
 *
 * @param connection  the connection (its phase is {@link ConnectionPhase#DISCONNECTED})
 * @param lastPhase   where it ended (e.g. {@link ConnectionPhase#AUTHENTICATING} for a failed login)
 * @param kind        the classified reason
 * @param message     Minecraft's message as plain text (may be empty)
 * @param reconnectIn milliseconds until Lumora reconnects by itself, -1 if it does not
 * @since 1.2.0
 */
public record ServerDisconnectedEvent(ServerConnection connection, ConnectionPhase lastPhase, DisconnectKind kind,
                                      String message, long reconnectIn) implements LumoraEvent {
}
