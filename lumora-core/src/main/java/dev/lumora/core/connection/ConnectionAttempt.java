package dev.lumora.core.connection;

/**
 * One connection attempt of Minecraft's own connect screen, seen through the version adapter. Lumora never opens a
 * connection itself: Minecraft does the handshake, login, encryption and the Mojang session join; this interface only
 * reads where it is and can cancel it. All methods run on the client thread.
 */
public interface ConnectionAttempt {
    /** Name of the server list entry (Minecraft sets it while connecting); "" if not known yet. */
    String serverName();

    /** Address as entered ("host" or "host:port"); "" if not known yet. */
    String serverAddress();

    /**
     * How far the network connection is: {@code ""} while resolving and connecting, then the protocol of the packet
     * listener: {@code "login"}, {@code "configuration"} (1.20.2+) or {@code "play"}; {@code "closed"} after it ended.
     */
    String stage();

    /** Translation key of Minecraft's status line ("connect.authorizing", "connect.encrypting", ...), "" if none. */
    String statusKey();

    /** The resolved remote socket address ("203.0.113.5:25565"), "" before the connection exists. */
    String remoteAddress();

    /** Stops the attempt like Minecraft's "Cancel" button and returns to the screen it came from. */
    void cancel();
}
