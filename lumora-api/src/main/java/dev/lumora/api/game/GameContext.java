package dev.lumora.api.game;

import java.util.Optional;

/**
 * Read-only game state, refreshed once per client tick (so reading it in a render method costs nothing).
 * Everything is empty while no world is loaded.
 */
public interface GameContext {
    /** A world is loaded and the local player exists. */
    boolean inWorld();

    Optional<PlayerSnapshot> player();

    /** Dimension id, e.g. "minecraft:overworld". */
    Optional<String> dimension();

    /** "survival", "creative", "adventure" or "spectator". */
    Optional<String> gameMode();

    ConnectionInfo connection();

    /** A menu is open (inventory, chat, pause, ...). */
    boolean screenOpen();

    /** Simple class name of the open screen. */
    Optional<String> screen();

    /** F1: the player hid the HUD. */
    boolean hudHidden();

    boolean windowFocused();

    int fps();

    /** Client ticks since start. */
    long clientTicks();
}
