package dev.lumora.client.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.render.ScissorStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/**
 * The part of Minecraft 1.20's {@code GuiGraphics} that the Lumora screens use, for Minecraft 1.8.9: there the GUI draws
 * with static {@link DrawableHelper} helpers on the fixed-function GL matrix stack ({@link Pose}). Every method maps to
 * the vanilla 1.8.9 call, so the screens keep the drawing code of the newer modules.
 */
public final class GuiGraphics {
    private static final Pose POSE = new Pose();
    private final MinecraftClient mc = MinecraftClient.getInstance();
    /** Read buffer of the GL model-view matrix (LWJGL 2 needs a direct buffer). Render thread only. */
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private GuiGraphics() {
    }

    /** A drawing context for the current frame (1.8.9 has no per-frame object; the GL state is global). */
    public static GuiGraphics create() {
        return new GuiGraphics();
    }

    public Pose pose() {
        return POSE;
    }

    public Font font() {
        return new Font(mc.textRenderer);
    }

    public int guiWidth() {
        return (int) new Window(mc).getScaledWidth();
    }

    public int guiHeight() {
        return (int) new Window(mc).getScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
        // fill leaves the colour of the last quad as current GL colour; texts and textures expect white
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    // --- text: "shadow" selects drawWithShadow vs draw; the overloads without it draw a shadow, like 1.20

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        // 1.8.9 treats an alpha of 0 as opaque; Lumora colours always carry their alpha
        return font.renderer().draw(text, (float) x, (float) y, color, shadow);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text.getString(), x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return drawString(font, text.getString(), x, y, color, shadow);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        drawString(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        drawCenteredString(font, text.getString(), centerX, y, color);
    }

    /** Wrapped text without shadow, like 1.20's drawWordWrap. */
    public void drawWordWrap(Font font, Component text, int x, int y, int width, int color) {
        for (String line : font.split(text, width)) {
            drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    // --- textures

    /** Region {@code u, v, uWidth x vHeight} of a {@code textureWidth x textureHeight} texture, stretched to {@code w x h}. */
    public void blit(Identifier texture, int x, int y, int w, int h, float u, float v, int uWidth, int vHeight,
                     int textureWidth, int textureHeight) {
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        DrawableHelper.drawTexture(x, y, u, v, uWidth, vHeight, w, h, textureWidth, textureHeight);
    }

    public void blit(Identifier texture, int x, int y, float u, float v, int w, int h, int textureWidth, int textureHeight) {
        mc.getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        DrawableHelper.drawTexture(x, y, u, v, w, h, textureWidth, textureHeight);
    }

    public void setColor(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
    }

    // --- scissor in GUI coordinates (1.8.9 has no helper). HudGraphics promises the current transformation and nesting,
    // so the rectangle goes through the GL model-view matrix (the Pose below is that stack) onto the shared
    // ScissorStack (intersection with the enclosing rectangle; disableScissor restores that one). Shared because a
    // wrapper is created per frame / call site while the GL scissor state is global.

    public void enableScissor(int x1, int y1, int x2, int y2) {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        float[] modelView = new float[16];
        MATRIX.get(modelView);
        apply(ScissorStack.GUI.push(ScissorStack.transform(x1, y1, x2, y2, modelView)));
    }

    public void disableScissor() {
        ScissorStack.Rect outer = ScissorStack.GUI.pop();
        if (outer == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(outer);
        }
    }

    private void apply(ScissorStack.Rect rect) {
        int[] px = rect.toWindow(new Window(mc).getScaleFactor(), mc.height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(px[0], px[1], px[2], px[3]);
    }

    /** All drawing is immediate in 1.8.9 - nothing is batched. */
    public void flush() {
    }

    public void renderItem(ItemStack stack, int x, int y) {
        DiffuseLighting.enable();
        mc.getItemRenderer().renderInGuiWithOverrides(stack, x, y);
        DiffuseLighting.disable();
        GlStateManager.enableBlend();
    }

    /** Count and durability bar of an item at (x, y), like the hotbar. */
    public void renderItemDecorations(ItemStack stack, int x, int y) {
        mc.getItemRenderer().renderGuiItemOverlay(mc.textRenderer, stack, x, y);
        GlStateManager.enableBlend();
    }

    /** The GL matrix stack under the 1.20 PoseStack names (2D GUI use: z is ignored by scale). */
    public static final class Pose {
        public void pushPose() {
            GlStateManager.pushMatrix();
        }

        public void popPose() {
            GlStateManager.popMatrix();
        }

        public void translate(float x, float y, float z) {
            GlStateManager.translate(x, y, z);
        }

        public void scale(float x, float y, float z) {
            GlStateManager.scale(x, y, z);
        }

        public void rotateZ(float degrees) {
            GlStateManager.rotate(degrees, 0f, 0f, 1f);
        }
    }
}
