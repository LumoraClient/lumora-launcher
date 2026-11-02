package dev.lumora.api.connection;

/**
 * Where a server connection is. The order is the order of a normal join; not every server goes through every phase
 * (offline-mode servers skip encryption and authentication, versions before 1.20.2 have no configuration phase).
 *
 * @since 1.2.0
 */
public enum ConnectionPhase {
    /** Resolving the address (SRV record) and opening the TCP connection. */
    CONNECTING,
    /** Handshake sent, login started. */
    LOGIN,
    /** Minecraft confirms the account with Mojang's session service (online-mode servers). */
    AUTHENTICATING,
    /** Keys are exchanged and the connection is encrypted (online-mode servers). */
    ENCRYPTING,
    /** Server sends registries, resource packs and settings (1.20.2 and later). */
    CONFIGURING,
    /** Loading the world. */
    JOINING,
    /** In the world. */
    PLAYING,
    /** The connection ended (see the disconnect event for why). */
    DISCONNECTED
}
