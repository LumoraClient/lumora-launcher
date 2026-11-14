package dev.lumora.api.chat;

import dev.lumora.api.render.HudGraphics;

/**
 * A window of a {@link ChatInputButton} above the chat input, right-aligned (like LabyMod's chat input tabs): opens
 * with a click on its button and closes with a second click, with {@link Chat#closePanel()} or when the chat closes.
 * Only one panel is open at a time. All coordinates are GUI pixels relative to the panel's top left corner; Lumora
 * draws the background and border, the panel draws its content inside {@code width() x height()}.
 *
 * <pre>{@code
 * ChatPanel panel = new ChatPanel() {
 *     public int width() { return 120; }
 *     public int height() { return 40; }
 *     public void render(HudGraphics g, int mouseX, int mouseY) {
 *         g.text("Hello", 4, 4, 0xFFFFFFFF, true);
 *     }
 *     public boolean mouseClicked(int mouseX, int mouseY, int button) {
 *         chat.insertText("Hello ");
 *         return true;
 *     }
 * };
 * }</pre>
 *
 * @since 1.9.0
 */
public interface ChatPanel {
    /** Width in GUI pixels; asked every frame, at most the screen width minus 4. */
    int width();

    /** Height in GUI pixels; asked every frame, Lumora shrinks it to the room above the chat input. */
    int height();

    /**
     * The size Lumora gives the panel in this frame (at most {@link #width()} x {@link #height()}, less when the
     * screen is small), called before every {@link #render}.
     */
    default void resized(int width, int height) {
    }

    /**
     * Draws the content (render thread, every frame while open).
     *
     * @param mouseX mouse position relative to the panel (may be outside it)
     */
    void render(HudGraphics g, int mouseX, int mouseY);

    /** A click inside the panel; {@code button} 0 left, 1 right, 2 middle. Returns whether it was used. */
    default boolean mouseClicked(int mouseX, int mouseY, int button) {
        return false;
    }

    /** The mouse wheel over the panel; {@code amount} positive = up. Returns whether it was used. */
    default boolean mouseScrolled(int mouseX, int mouseY, double amount) {
        return false;
    }

    /** The panel was opened (its button clicked). */
    default void opened() {
    }

    /** The panel was closed (button, {@link Chat#closePanel()}, chat closed or addon disabled). */
    default void closed() {
    }
}
