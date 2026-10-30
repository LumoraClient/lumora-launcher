package dev.lumora.api.input;

import java.util.List;

/**
 * Keybinds that can be added and removed at runtime (unlike Minecraft's own key mappings, which are fixed after
 * start). They react only in game (no menu open, window focused), once per key press.
 */
public interface KeybindRegistry {
    /**
     * Registers {@code <addon>:<id>}. A key chosen by the user earlier is restored. If the default key is already
     * used by Minecraft or another keybind, the keybind starts unbound (logged) instead of clashing.
     *
     * @param onPress runs on the client thread when the key goes down
     */
    Keybind register(String id, String nameKey, int defaultKey, Runnable onPress);

    /** Keybinds of this owner. */
    List<Keybind> keybinds();

    /** Localised key name, e.g. "C" or "Right Shift". */
    String keyName(int key);
}
