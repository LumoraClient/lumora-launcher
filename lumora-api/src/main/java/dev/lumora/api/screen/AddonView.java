package dev.lumora.api.screen;

import dev.lumora.api.render.HudGraphics;

/**
 * A full-screen menu of an addon, drawn with {@link HudGraphics} and operated without Minecraft classes. Lumora shows
 * it in a screen of the running Minecraft version and forwards drawing and input: GUI coordinates, mouse button 0 =
 * left, key codes from {@link dev.lumora.api.input.Keys} (also on versions with LWJGL 2).
 *
 * @since 1.1.0
 */
public interface AddonView {
    void render(HudGraphics graphics, int width, int height, int mouseX, int mouseY);

    default boolean mouseClicked(double x, double y, int button) { return false; }

    default boolean mouseReleased(double x, double y, int button) { return false; }

    default boolean mouseDragged(double x, double y, int button) { return false; }

    /** {@code delta} > 0 scrolls up. */
    default boolean mouseScrolled(double x, double y, double delta) { return false; }

    default boolean keyPressed(int key, boolean ctrl, boolean shift) { return false; }

    default boolean charTyped(char ch) { return false; }

    /** Escape was pressed; {@code false} keeps the view open. */
    default boolean closeRequested() { return true; }

    /** The view is no longer shown (closed, replaced by the game, or the addon was disabled). */
    default void removed() { }
}
