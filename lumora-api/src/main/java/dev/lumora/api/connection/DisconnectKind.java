package dev.lumora.api.connection;

/**
 * Why a connection ended, classified from Minecraft's disconnect message (translation keys first, then well known
 * texts of server software). {@link #retryable()} says whether trying again without changing anything can help.
 *
 * @since 1.2.0
 */
public enum DisconnectKind {
    /** The player left or cancelled. */
    USER(false),
    /** No answer in time (connect or read timeout). */
    TIMEOUT(true),
    /** Nothing listens on the address (connection refused) or the network is unreachable. */
    REFUSED(true),
    /** The host name does not exist. */
    UNKNOWN_HOST(false),
    /** The connection broke (end of stream, connection reset, lost). */
    LOST(true),
    /** The account could not be verified (invalid session, auth servers down, multiplayer disabled). */
    AUTH(false),
    /** Banned from the server (or the account from online play). */
    BANNED(false),
    /** Not on the whitelist. */
    WHITELIST(false),
    /** Server full. */
    FULL(true),
    /** Client and server version do not match. */
    OUTDATED(false),
    /** Too many connections in a short time (connection throttled). */
    THROTTLED(true),
    /** The server is stopping or restarting. */
    SHUTDOWN(true),
    /** Logged in from another place. */
    DUPLICATE_LOGIN(false),
    /** Kicked by the server or an operator (with or without a reason). */
    KICKED(false),
    /** Protocol or packet error. */
    PROTOCOL(false),
    /** Anything else. */
    OTHER(true);

    private final boolean retryable;

    DisconnectKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
