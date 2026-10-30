package dev.lumora.client.api;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link HudGraphics} on top of the 26.1 GuiGraphicsExtractor. One instance is reused for every frame
 * ({@link #with}); texture ids are cached, so drawing allocates nothing.
 */
public final class McHudGraphics implements HudGraphics {
    private static final int MAX_CACHED_TEXTURES = 512;
    private final Map<TextureRef, Identifier> textures = new HashMap<>();
    private GuiGraphicsExtractor g;

    public McHudGraphics with(GuiGraphicsExtractor graphics) {
        this.g = graphics;
        return this;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public int textWidth(String text) {
        return font().width(text);
    }

    @Override
    public int lineHeight() {
        return font().lineHeight;
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        g.text(font(), text, x, y, color, shadow);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void texture(TextureRef texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight,
                        int textureWidth, int textureHeight) {
        Identifier id = textures.get(texture);
        if (id == null) {
            if (textures.size() >= MAX_CACHED_TEXTURES) {
                textures.clear();
            }
            id = Identifier.fromNamespaceAndPath(texture.namespace(), texture.path());
            textures.put(texture, id);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    @Override
    public void push() {
        g.pose().pushMatrix();
    }

    @Override
    public void pop() {
        g.pose().popMatrix();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y);
    }

    @Override
    public void scale(float x, float y) {
        g.pose().scale(x, y);
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        g.enableScissor(x1, y1, x2, y2);
    }

    @Override
    public void disableScissor() {
        g.disableScissor();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(g) ? Optional.of(type.cast(g)) : Optional.empty();
    }
}
