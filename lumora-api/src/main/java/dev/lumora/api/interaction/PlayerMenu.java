package dev.lumora.api.interaction;

import dev.lumora.api.Registration;

import java.util.List;

/**
 * The player menu (permission {@code playermenu}): look at a player and hold the middle mouse button (the mouse wheel),
 * the menu appears as a ring around the player; release over an entry to run it, or turn the mouse wheel to choose.
 * Lumora's own entries (friend, party, profile, name, messages) come first; addons add theirs. Entries of the same
 * {@linkplain PlayerMenuEntry.Builder#category category} share one segment of the ring and open as an outer ring in
 * the same menu.
 *
 * @since 1.4.0
 */
public interface PlayerMenu {
    /** Adds {@code entry}; removed with the returned registration or when the addon is disabled. Ids are unique per addon. */
    Registration add(PlayerMenuEntry entry);

    /** Entries this addon added. */
    List<PlayerMenuEntry> entries();
}
