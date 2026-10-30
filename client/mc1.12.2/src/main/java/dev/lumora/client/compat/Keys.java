package dev.lumora.client.compat;

import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft 1.12.2 uses LWJGL 2 key codes ({@link Keyboard}); the Lumora API and the screens use GLFW key codes
 * ({@link dev.lumora.api.input.Keys}, like Minecraft 1.13+). This adapter translates both ways; keys without a
 * counterpart map to {@link #NONE}.
 */
public final class Keys {
    public static final int NONE = -1;
    private static final Map<Integer, Integer> TO_GLFW = new HashMap<>();
    private static final Map<Integer, Integer> TO_LWJGL = new HashMap<>();

    static {
        // letters: GLFW uses ASCII 'A'..'Z'
        int[] letters = {Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C, Keyboard.KEY_D, Keyboard.KEY_E, Keyboard.KEY_F,
                Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I, Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L, Keyboard.KEY_M,
                Keyboard.KEY_N, Keyboard.KEY_O, Keyboard.KEY_P, Keyboard.KEY_Q, Keyboard.KEY_R, Keyboard.KEY_S, Keyboard.KEY_T,
                Keyboard.KEY_U, Keyboard.KEY_V, Keyboard.KEY_W, Keyboard.KEY_X, Keyboard.KEY_Y, Keyboard.KEY_Z};
        for (int i = 0; i < letters.length; i++) {
            put(letters[i], 'A' + i);
        }
        // digits: GLFW uses ASCII '0'..'9'
        int[] digits = {Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5,
                Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9};
        for (int i = 0; i < digits.length; i++) {
            put(digits[i], '0' + i);
        }
        // F1..F12 = GLFW 290..301, F13..F15 = 302..304
        int[] fkeys = {Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6,
                Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F11, Keyboard.KEY_F12,
                Keyboard.KEY_F13, Keyboard.KEY_F14, Keyboard.KEY_F15};
        for (int i = 0; i < fkeys.length; i++) {
            put(fkeys[i], 290 + i);
        }
        // keypad 0..9 = GLFW 320..329
        int[] pad = {Keyboard.KEY_NUMPAD0, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3, Keyboard.KEY_NUMPAD4,
                Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7, Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
        for (int i = 0; i < pad.length; i++) {
            put(pad[i], 320 + i);
        }
        put(Keyboard.KEY_SPACE, 32);
        put(Keyboard.KEY_APOSTROPHE, 39);
        put(Keyboard.KEY_COMMA, 44);
        put(Keyboard.KEY_MINUS, 45);
        put(Keyboard.KEY_PERIOD, 46);
        put(Keyboard.KEY_SLASH, 47);
        put(Keyboard.KEY_SEMICOLON, 59);
        put(Keyboard.KEY_EQUALS, 61);
        put(Keyboard.KEY_LBRACKET, 91);
        put(Keyboard.KEY_BACKSLASH, 92);
        put(Keyboard.KEY_RBRACKET, 93);
        put(Keyboard.KEY_GRAVE, 96);
        put(Keyboard.KEY_ESCAPE, 256);
        put(Keyboard.KEY_RETURN, 257);
        put(Keyboard.KEY_TAB, 258);
        put(Keyboard.KEY_BACK, 259);
        put(Keyboard.KEY_INSERT, 260);
        put(Keyboard.KEY_DELETE, 261);
        put(Keyboard.KEY_RIGHT, 262);
        put(Keyboard.KEY_LEFT, 263);
        put(Keyboard.KEY_DOWN, 264);
        put(Keyboard.KEY_UP, 265);
        put(Keyboard.KEY_PRIOR, 266);
        put(Keyboard.KEY_NEXT, 267);
        put(Keyboard.KEY_HOME, 268);
        put(Keyboard.KEY_END, 269);
        put(Keyboard.KEY_CAPITAL, 280);
        put(Keyboard.KEY_SCROLL, 281);
        put(Keyboard.KEY_NUMLOCK, 282);
        put(Keyboard.KEY_PAUSE, 284);
        put(Keyboard.KEY_DECIMAL, 330);
        put(Keyboard.KEY_DIVIDE, 331);
        put(Keyboard.KEY_MULTIPLY, 332);
        put(Keyboard.KEY_SUBTRACT, 333);
        put(Keyboard.KEY_ADD, 334);
        put(Keyboard.KEY_NUMPADENTER, 335);
        put(Keyboard.KEY_NUMPADEQUALS, 336);
        put(Keyboard.KEY_LSHIFT, 340);
        put(Keyboard.KEY_LCONTROL, 341);
        put(Keyboard.KEY_LMENU, 342);
        put(Keyboard.KEY_LMETA, 343);
        put(Keyboard.KEY_RSHIFT, 344);
        put(Keyboard.KEY_RCONTROL, 345);
        put(Keyboard.KEY_RMENU, 346);
        put(Keyboard.KEY_RMETA, 347);
    }

    private Keys() {
    }

    private static void put(int lwjgl, int glfw) {
        TO_GLFW.put(lwjgl, glfw);
        TO_LWJGL.put(glfw, lwjgl);
    }

    public static int toGlfw(int lwjgl) {
        return TO_GLFW.getOrDefault(lwjgl, NONE);
    }

    /** LWJGL 2 code for a GLFW code; {@link Keyboard#KEY_NONE} if there is none. */
    public static int toLwjgl(int glfw) {
        return TO_LWJGL.getOrDefault(glfw, Keyboard.KEY_NONE);
    }
}
