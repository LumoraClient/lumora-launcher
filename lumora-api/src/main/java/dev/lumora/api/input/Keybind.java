package dev.lumora.api.input;

import dev.lumora.api.Registration;

import java.util.Optional;

/** A Lumora keybind. The user can change the key in the Lumora hub ("Addons" / widget settings). */
public interface Keybind extends Registration {
    /** {@code addon:id}. */
    String id();

    String nameKey();

    int defaultKey();

    /** Current key, {@link Keys#UNBOUND} if none. */
    int key();

    /**
     * Changes the key. Rejected (returns {@code false}) if the key is not bindable or already used by a Minecraft
     * or another Lumora keybind - {@link #conflictWith(int)} tells which.
     */
    boolean setKey(int key);

    /** Name of the keybind that already uses {@code key}, if any. */
    Optional<String> conflictWith(int key);

    /** {@code true} while the key is held (only in game, not in menus). */
    boolean isDown();
}
