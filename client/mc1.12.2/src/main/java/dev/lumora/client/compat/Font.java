package dev.lumora.client.compat;

import net.minecraft.client.font.TextRenderer;

import java.util.List;

/** The modern {@code Font} methods Lumora uses, on top of the 1.12.2 {@link TextRenderer} (strings with format codes). */
public final class Font {
    public final int lineHeight;
    private final TextRenderer renderer;

    private static Font current;

    public Font(TextRenderer renderer) {
        this.renderer = renderer;
        this.lineHeight = renderer.fontHeight;
    }

    /** The game's font ({@code Minecraft.getInstance().font} in the newer versions). */
    public static Font get() {
        TextRenderer r = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        if (current == null || current.renderer != r) {
            current = new Font(r);
        }
        return current;
    }

    public TextRenderer renderer() {
        return renderer;
    }

    public int width(String text) {
        return renderer.getStringWidth(text);
    }

    public int width(Component text) {
        return renderer.getStringWidth(text.getString());
    }

    /** Lines of at most {@code width} pixels (word wrap, like the vanilla tooltip and chat). */
    public List<String> split(Component text, int width) {
        return renderer.wrapLines(text.getString(), width);
    }

    public List<String> split(String text, int width) {
        return renderer.wrapLines(text, width);
    }
}
