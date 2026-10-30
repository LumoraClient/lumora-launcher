package dev.lumora.api.input;

/** Keyboard key codes (identical to GLFW, which Minecraft uses). */
public final class Keys {
    public static final int UNBOUND = -1;
    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int NUM_0 = 48;
    public static final int NUM_9 = 57;
    public static final int A = 65;
    public static final int B = 66;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int G = 71;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int T = 84;
    public static final int U = 85;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int F1 = 290;
    public static final int F12 = 301;
    public static final int KP_0 = 320;
    public static final int KP_9 = 329;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
    /** Highest GLFW key code. */
    public static final int LAST = 348;

    private Keys() {
    }

    /** {@code true} for {@link #UNBOUND} and every real GLFW key code. */
    public static boolean isValid(int key) {
        return key == UNBOUND || (key >= SPACE && key <= LAST);
    }

    /** Escape cannot be bound (it always closes menus). */
    public static boolean isBindable(int key) {
        return key != ESCAPE && isValid(key);
    }
}
