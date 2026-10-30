package dev.lumora.api.game;

/** Where the player is playing. {@code address} is empty unless on a server; {@code ping} is -1 if unknown. */
public record ConnectionInfo(Kind kind, String address, int ping) {
    public enum Kind { NONE, SINGLEPLAYER, MULTIPLAYER }

    public static final ConnectionInfo NONE = new ConnectionInfo(Kind.NONE, "", -1);

    public boolean multiplayer() {
        return kind == Kind.MULTIPLAYER;
    }
}
