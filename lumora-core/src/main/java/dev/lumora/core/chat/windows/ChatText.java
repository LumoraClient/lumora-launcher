package dev.lumora.core.chat.windows;

import dev.lumora.api.render.HudGraphics;

import java.util.List;

/**
 * What a Minecraft version gives the Lumora chat windows: its own text of the messages (colors, fonts, click and
 * hover events stay Minecraft's), the player heads and the chat options. Messages, lines, styles and skins are
 * Minecraft objects the core only passes back. Everything is called on the client thread.
 */
public interface ChatText {
    /** The message wrapped to lines of at most {@code width} pixels (unscaled). */
    List<Object> wrap(Object message, int width);

    /** Draws one line; {@code color} carries the alpha (white text keeps the message's own colors). */
    void draw(HudGraphics g, Object line, int x, int y, int color, boolean shadow);

    /** The style of the text at {@code x} pixels from the line's start, null if there is none. */
    Object styleAt(Object line, int x);

    /** Shows the hover text of a style (tooltip at the mouse); no-op for a style without one. */
    void hover(HudGraphics g, Object style, int mouseX, int mouseY);

    /** Runs the click event or the shift-click insertion of a style in the open chat; true if there was one. */
    boolean click(Object style, boolean shift);

    /** The skin of a player of the tab list, for the head in front of the message; null if the player is not there. */
    Object head(String name);

    /** Draws a skin's face with its hat layer. */
    void drawHead(HudGraphics g, Object head, int x, int y, int size);

    /** Minecraft's chat options. */
    Options options();

    /**
     * Everything drawn after this lies on top of what was drawn before, text included (versions that draw text after
     * all rectangles of a layer); called between two windows so a window covers the one below it.
     */
    default void layer(HudGraphics g) {
    }

    /** Plain text, insertions, suggested and run commands and links of a message (for sender and menu). */
    Parts parts(Object message);

    /**
     * @param scale             chat text size (Minecraft: Chat Text Size)
     * @param lineSpacing       extra space between lines, 0..1
     * @param textOpacity       0..1
     * @param backgroundOpacity 0..1
     * @param width             chat width in pixels
     * @param height            open chat height in pixels
     * @param heightClosed      closed chat height in pixels
     * @param hidden            chat set to hidden
     * @param bottom            distance of Minecraft's chat from the bottom of the screen (where the main window starts)
     */
    record Options(double scale, double lineSpacing, double textOpacity, double backgroundOpacity, int width, int height,
                   int heightClosed, boolean hidden, int bottom) {
        public static final Options DEFAULT = new Options(1, 0, 1, 0.5, 320, 180, 90, false);

        /** Minecraft's chat 40 pixels above the bottom (1.13 and newer). */
        public Options(double scale, double lineSpacing, double textOpacity, double backgroundOpacity, int width, int height,
                       int heightClosed, boolean hidden) {
            this(scale, lineSpacing, textOpacity, backgroundOpacity, width, height, heightClosed, hidden, 40);
        }
    }

    record Parts(String text, List<String> insertions, List<String> commands, List<String> links) {
    }
}
