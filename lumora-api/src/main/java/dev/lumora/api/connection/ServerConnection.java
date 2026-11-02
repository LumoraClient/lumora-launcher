package dev.lumora.api.connection;

/**
 * A server connection as Lumora sees it.
 *
 * @param name          the server list name, or the address for direct connections
 * @param address       what the player entered ("host" or "host:port"); empty if unknown
 * @param phase         current phase
 * @param startedMillis {@link System#currentTimeMillis()} when connecting started
 * @param joinedMillis  when the world was reached, 0 before that
 * @since 1.2.0
 */
public record ServerConnection(String name, String address, ConnectionPhase phase, long startedMillis, long joinedMillis) {
    public boolean joined() {
        return joinedMillis > 0;
    }
}
