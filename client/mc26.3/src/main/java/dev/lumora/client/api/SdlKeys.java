package dev.lumora.client.api;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft 26.3 switched from GLFW to SDL3: key codes are SDL scancodes (USB HID usage ids) instead of GLFW key
 * codes. The Lumora API keeps GLFW codes ({@link dev.lumora.api.input.Keys}) on every version, so this adapter
 * translates both ways. Keys without an SDL counterpart map to {@link #NONE}. Mouse buttons differ as well (SDL:
 * left 1, middle 2, right 3; GLFW and the Lumora API: left 0, right 1, middle 2), see {@link #glfwButton}.
 * Every screen that hands key codes or mouse buttons to Lumora code (addon views, HUD, editor) translates here.
 */
public final class SdlKeys {
    public static final int NONE = -1;
    private static final Map<Integer, Integer> TO_SDL = new HashMap<>();
    private static final Map<Integer, Integer> TO_GLFW = new HashMap<>();

    static {
        // letters: GLFW 65..90 -> SDL 4..29; digits: GLFW 48 ('0') .. 57 -> SDL 39 ('0'), 30..38 ('1'..'9')
        for (int i = 0; i < 26; i++) {
            put(65 + i, InputConstants.KEY_A + i);
        }
        put(48, InputConstants.KEY_0);
        for (int i = 1; i <= 9; i++) {
            put(48 + i, InputConstants.KEY_1 + i - 1);
        }
        // F1..F12 (GLFW 290..301) and F13..F24 (302..313)
        for (int i = 0; i < 12; i++) {
            put(290 + i, InputConstants.KEY_F1 + i);
            put(302 + i, InputConstants.KEY_F13 + i);
        }
        put(32, InputConstants.KEY_SPACE);
        put(39, InputConstants.KEY_APOSTROPHE);
        put(44, InputConstants.KEY_COMMA);
        put(45, InputConstants.KEY_MINUS);
        put(46, InputConstants.KEY_PERIOD);
        put(47, InputConstants.KEY_SLASH);
        put(59, InputConstants.KEY_SEMICOLON);
        put(61, InputConstants.KEY_EQUALS);
        put(91, InputConstants.KEY_LBRACKET);
        put(92, InputConstants.KEY_BACKSLASH);
        put(93, InputConstants.KEY_RBRACKET);
        put(96, InputConstants.KEY_GRAVE);
        put(256, InputConstants.KEY_ESCAPE);
        put(257, InputConstants.KEY_RETURN);
        put(258, InputConstants.KEY_TAB);
        put(259, InputConstants.KEY_BACKSPACE);
        put(260, InputConstants.KEY_INSERT);
        put(261, InputConstants.KEY_DELETE);
        put(262, InputConstants.KEY_RIGHT);
        put(263, InputConstants.KEY_LEFT);
        put(264, InputConstants.KEY_DOWN);
        put(265, InputConstants.KEY_UP);
        put(266, InputConstants.KEY_PAGEUP);
        put(267, InputConstants.KEY_PAGEDOWN);
        put(268, InputConstants.KEY_HOME);
        put(269, InputConstants.KEY_END);
        put(280, InputConstants.KEY_CAPSLOCK);
        put(281, InputConstants.KEY_SCROLLLOCK);
        put(282, InputConstants.KEY_NUMLOCK);
        put(283, InputConstants.KEY_PRINTSCREEN);
        put(284, InputConstants.KEY_PAUSE);
        // keypad: GLFW 320 ('0') .. 329 -> SDL KEY_NUMPAD0 / KEY_NUMPAD1..9
        put(320, InputConstants.KEY_NUMPAD0);
        for (int i = 1; i <= 9; i++) {
            put(320 + i, InputConstants.KEY_NUMPAD1 + i - 1);
        }
        put(332, InputConstants.KEY_MULTIPLY);
        put(334, InputConstants.KEY_ADD);
        put(335, InputConstants.KEY_NUMPADENTER);
        put(336, InputConstants.KEY_NUMPADEQUALS);
        put(340, InputConstants.KEY_LSHIFT);
        put(341, InputConstants.KEY_LCONTROL);
        put(342, InputConstants.KEY_LALT);
        put(343, InputConstants.KEY_LGUI);
        put(344, InputConstants.KEY_RSHIFT);
        put(345, InputConstants.KEY_RCONTROL);
        put(346, InputConstants.KEY_RALT);
        put(347, InputConstants.KEY_RGUI);
    }

    private SdlKeys() {
    }

    private static void put(int glfw, int sdl) {
        TO_SDL.put(glfw, sdl);
        TO_GLFW.put(sdl, glfw);
    }

    public static int toSdl(int glfw) {
        return TO_SDL.getOrDefault(glfw, NONE);
    }

    public static int toGlfw(int sdl) {
        return TO_GLFW.getOrDefault(sdl, NONE);
    }

    /** SDL mouse button (left 1, middle 2, right 3, X1 4, X2 5) to GLFW / Lumora API (left 0, right 1, middle 2, 3, 4). */
    public static int glfwButton(int sdl) {
        return switch (sdl) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> sdl - 1;
        };
    }
}
