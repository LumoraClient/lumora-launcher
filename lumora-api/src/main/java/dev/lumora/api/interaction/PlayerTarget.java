package dev.lumora.api.interaction;

import java.util.UUID;

/**
 * The player the menu was opened on, with what Lumora knows about the two of you. Social facts come from the Lumora
 * social server: they are {@code false} while it has not answered yet or when it is not connected.
 *
 * @since 1.4.0
 */
public interface PlayerTarget {
    UUID id();

    String name();

    /** On a server (not in singleplayer). */
    boolean multiplayer();

    /** The player uses Lumora. */
    boolean lumoraUser();

    /** The player is your Lumora friend. */
    boolean friend();

    /** The player is in your Lumora party. */
    boolean inYourParty();

    /** You lead a Lumora party (you may invite and remove). */
    boolean partyLeader();

    /** Your operator level on the current server or world, 0 (none, or not known on this Minecraft version) to 4. */
    int permissionLevel();
}
