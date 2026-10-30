package dev.lumora.core.input;

import java.util.Map;

/** Keyboard access of the running Minecraft version. */
public interface KeyPlatform {
    /** Physical key state (GLFW code). */
    boolean isDown(int key);

    /** Localised key name. */
    String keyName(int key);

    /** Keys used by Minecraft's own key mappings (vanilla and other mods): key code to localised mapping name. */
    Map<Integer, String> minecraftBindings();

    /** Keybinds may fire: in a world, no screen open, window focused. */
    boolean acceptsKeybinds();
}
