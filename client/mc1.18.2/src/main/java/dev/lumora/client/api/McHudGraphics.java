package dev.lumora.client.api;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link HudGraphics} on top of the 26.1 GuiGraphics. One instance is reused for every frame
 * ({@link #with}); texture ids are cached, so drawing allocates nothing.
 */
public final class McHudGraphics implements HudGraphics, dev.lumora.core.perf.FillBatch {
    private static final int MAX_CACHED_TEXTURES = 512;
    private final Map<TextureRef, ResourceLocation> textures = new HashMap<>();
    private GuiGraphics g;

    /** The Minecraft graphics of this frame (Lumora chat windows draw Minecraft text through it). */
    public GuiGraphics graphics() {
        return g;
    }

    public McHudGraphics with(GuiGraphics graphics) {
        this.g = graphics;
        if (dev.lumora.core.perf.PerformanceEngine.smartHud) {
            dev.lumora.core.perf.TextWidths.validate(fontState()); // font switched without a resource reload
        }
        return this;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public int textWidth(String text) {
        if (!dev.lumora.core.perf.PerformanceEngine.smartHud) {
            return font().width(text);
        }
        int w = dev.lumora.core.perf.TextWidths.get(text);
        if (w < 0) {
            w = font().width(text);
            dev.lumora.core.perf.TextWidths.put(text, w);
        }
        return w;
    }

    @Override
    public void beginFills() {
        g.beginFills();
    }

    @Override
    public void endFills() {
        g.endFills();
    }

    /** What decides a text's width besides the text: the font options that change the font without a resource reload. */
    public static int fontState() {
        return Minecraft.getInstance().options.forceUnicodeFont ? 1 : 0;
    }

    @Override
    public int lineHeight() {
        return font().lineHeight;
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        g.drawString(font(), text, x, y, color, shadow);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    /** Frame optimization on: same pixels with cached corner shapes and fewer rectangles. */
    @Override
    public void roundedRect(int x, int y, int width, int height, int radius, int color) {
        if (dev.lumora.core.perf.PerformanceEngine.frame) {
            dev.lumora.core.perf.FastShapes.roundedRect(this, x, y, width, height, radius, color);
        } else {
            HudGraphics.super.roundedRect(x, y, width, height, radius, color);
        }
    }

    @Override
    public void texture(TextureRef texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight,
                        int textureWidth, int textureHeight) {
        ResourceLocation id = textures.get(texture);
        if (id == null) {
            if (textures.size() >= MAX_CACHED_TEXTURES) {
                textures.clear();
            }
            id = new ResourceLocation(texture.namespace(), texture.path());
            textures.put(texture, id);
        }
        g.blit(id, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    @Override
    public void push() {
        g.pose().pushPose();
    }

    @Override
    public void pop() {
        g.pose().popPose();
    }

    @Override
    public void translate(float x, float y) {
        g.pose().translate(x, y, 0);
    }

    @Override
    public void scale(float x, float y) {
        g.pose().scale(x, y, 1);
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
