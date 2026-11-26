package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import java.util.*;

/** Records what a view draws; text is 6 px per character like the Minecraft font's average. */
final class FakeGraphics implements HudGraphics {
    final List<String> texts = new ArrayList<>();
    final List<TextureRef> textures = new ArrayList<>();
    int fills, depth, scissors;

    @Override public int textWidth(String text) { return text.length() * 6; }
    @Override public int lineHeight() { return 9; }
    @Override public void text(String text, int x, int y, int color, boolean shadow) { texts.add(text); }
    @Override public void fill(int x1, int y1, int x2, int y2, int color) { fills++; }
    @Override public void texture(TextureRef texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) { textures.add(texture); }
    @Override public void push() { depth++; }
    @Override public void pop() { depth--; if (depth < 0) throw new IllegalStateException("pop without push"); }
    @Override public void translate(float x, float y) {}
    @Override public void scale(float x, float y) {}
    @Override public void enableScissor(int x1, int y1, int x2, int y2) { scissors++; }
    @Override public void disableScissor() { scissors--; }
    @Override public <T> Optional<T> unwrap(Class<T> type) { return Optional.empty(); }

    boolean drew(String text) { return texts.stream().anyMatch(t -> t.contains(text)); }
}
