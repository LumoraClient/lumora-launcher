package dev.lumora.api.render;

import java.util.Optional;

/**
 * Drawing surface for HUD widgets, implemented on top of Minecraft's GUI renderer of the running version.
 * Coordinates are GUI pixels; inside {@link dev.lumora.api.hud.HudWidget#render} the origin is the widget's top
 * left corner and the widget scale is already applied. Colors are ARGB ({@code 0xAARRGGBB}; alpha 0 = invisible).
 * <p>
 * Only valid during the call that received it (do not store it). Every {@link #push()} needs a matching
 * {@link #pop()}.
 */
public interface HudGraphics {
    /** Width of the text in the current font, unscaled. */
    int textWidth(String text);

    /** Height of one text line (9 with the default font). */
    int lineHeight();

    void text(String text, int x, int y, int color, boolean shadow);

    default void text(String text, int x, int y, int color) {
        text(text, x, y, color, true);
    }

    default void centeredText(String text, int centerX, int y, int color, boolean shadow) {
        text(text, centerX - textWidth(text) / 2, y, color, shadow);
    }

    default void rightText(String text, int right, int y, int color, boolean shadow) {
        text(text, right - textWidth(text), y, color, shadow);
    }

    /** Filled rectangle from (x1, y1) inclusive to (x2, y2) exclusive. */
    void fill(int x1, int y1, int x2, int y2, int color);

    default void rect(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + height, color);
    }

    /** 1px outline inside the given rectangle. */
    default void outline(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** Rounded rectangle; the parts do not overlap, so translucent colors stay even. */
    default void roundedRect(int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        fill(x + r, y, x + width - r, y + height, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - d * d));
            if (inset >= r) {
                continue;
            }
            fill(x + inset, y + i, x + r, y + i + 1, color);
            fill(x + width - r, y + i, x + width - inset, y + i + 1, color);
            fill(x + inset, y + height - i - 1, x + r, y + height - i, color);
            fill(x + width - r, y + height - i - 1, x + width - inset, y + height - i, color);
        }
        if (height > 2 * r) {
            fill(x, y + r, x + r, y + height - r, color);
            fill(x + width - r, y + r, x + width, y + height - r, color);
        }
    }

    /** Lumora card: rounded panel with a 1px border. */
    default void card(int x, int y, int width, int height, int radius, int fill, int border) {
        roundedRect(x, y, width, height, radius, border);
        roundedRect(x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1), fill);
    }

    /**
     * Draws a region of a texture. (u, v) and the region size are in texture pixels; the texture size must be the
     * real size of the image.
     */
    void texture(TextureRef texture, int x, int y, int width, int height, float u, float v,
                 int regionWidth, int regionHeight, int textureWidth, int textureHeight);

    /** Draws a whole square or rectangular texture of the given size into the rectangle. */
    default void texture(TextureRef texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        texture(texture, x, y, width, height, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Saves the current transformation. */
    void push();

    /** Restores the transformation saved by the matching {@link #push()}. */
    void pop();

    void translate(float x, float y);

    void scale(float x, float y);

    default void scale(float factor) {
        scale(factor, factor);
    }

    /** Limits drawing to the rectangle (current transformation applies). Calls can be nested. */
    void enableScissor(int x1, int y1, int x2, int y2);

    void disableScissor();

    /**
     * The renderer of the Minecraft version, for version-specific drawing (items, entities, custom fonts), e.g.
     * {@code g.unwrap(GuiGraphicsExtractor.class)} on 26.1.2. Code using it only works on that version.
     */
    <T> Optional<T> unwrap(Class<T> type);
}
