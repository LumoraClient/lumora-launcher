package dev.lumora.core;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Records drawing calls; 6 px per character, 9 px lines. Checks push/pop balance. */
public final class FakeGraphics implements HudGraphics {
    public final List<String> texts = new ArrayList<>();
    public int fills;
    public int depth;
    public int maxDepth;
    public float tx;
    public float ty;

    @Override
    public int textWidth(String text) {
        return text.length() * 6;
    }

    @Override
    public int lineHeight() {
        return 9;
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        texts.add(text);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        fills++;
    }

    @Override
    public void texture(TextureRef texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight,
                        int textureWidth, int textureHeight) {
        fills++;
    }

    @Override
    public void push() {
        depth++;
        maxDepth = Math.max(maxDepth, depth);
    }

    @Override
    public void pop() {
        depth--;
        if (depth < 0) {
            throw new IllegalStateException("pop without push");
        }
    }

    @Override
    public void translate(float x, float y) {
        tx += x;
        ty += y;
    }

    @Override
    public void scale(float x, float y) {
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
    }

    @Override
    public void disableScissor() {
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return Optional.empty();
    }
}
