package dev.lumora.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.core.render.ScissorStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The part of Minecraft 1.20's {@code GuiGraphics} that the Lumora screens use, for Minecraft 1.19.4 and older: there
 * the GUI draws through a {@link PoseStack} and the static {@link GuiComponent} helpers. One wrapper per frame / call
 * site ({@link #of}); every method maps 1:1 to the vanilla call of this version, so screens and widgets keep the
 * same drawing code as the 1.20+ modules.
 */
public final class GuiGraphics {
    private final PoseStack pose;
    private final Minecraft mc = Minecraft.getInstance();

    private GuiGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public static GuiGraphics of(PoseStack pose) {
        return new GuiGraphics(pose);
    }

    public PoseStack pose() {
        return pose;
    }

    public int guiWidth() {
        return mc.getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return mc.getWindow().getGuiScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        if (fillRuns > 0) {
            fillQuad(x1, y1, x2, y2, color);
            return;
        }
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    // Frame Optimization: a run of rectangles drawn with one draw call. Same vertices, colours and GL state as
    // GuiComponent.fill, in the same order, so overlapping translucent rectangles blend exactly as one by one.
    private int fillRuns;
    private com.mojang.blaze3d.vertex.BufferBuilder fillBuilder;

    public void beginFills() {
        fillRuns++;
    }

    public void endFills() {
        if (fillRuns > 0 && --fillRuns == 0 && fillBuilder != null) {
            var builder = fillBuilder;
            fillBuilder = null;
            RenderSystem.enableBlend();
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(builder.end());
            RenderSystem.disableBlend();
        }
    }

    private void fillQuad(int x1, int y1, int x2, int y2, int color) {
        if (x1 < x2) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y1 < y2) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        var builder = fillBuilder;
        if (builder == null) {
            builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            builder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            fillBuilder = builder;
        }
        var m = pose.last().pose();
        builder.vertex(m, (float) x1, (float) y2, 0.0F).color(r, g, b, a).endVertex();
        builder.vertex(m, (float) x2, (float) y2, 0.0F).color(r, g, b, a).endVertex();
        builder.vertex(m, (float) x2, (float) y1, 0.0F).color(r, g, b, a).endVertex();
        builder.vertex(m, (float) x1, (float) y1, 0.0F).color(r, g, b, a).endVertex();
    }

    // --- text: "shadow" selects drawShadow vs draw, the 1.20 overloads without it draw a shadow

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, centerX, y, color);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, centerX, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, centerX, y, color);
    }

    /** Wrapped text without shadow, like 1.20's drawWordWrap. */
    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            font.draw(pose, line, x, y, color);
            y += font.lineHeight;
        }
    }

    // --- textures: 1.20 binds the texture per call, 1.19.4 through the shader texture slot

    /** Region {@code u, v, uWidth x vHeight} of a {@code textureWidth x textureHeight} texture, stretched to {@code w x h}. */
    public void blit(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uWidth, int vHeight,
                     int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, w, h, u, v, uWidth, vHeight, textureWidth, textureHeight);
    }

    public void blit(ResourceLocation texture, int x, int y, float u, float v, int w, int h, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, u, v, w, h, textureWidth, textureHeight);
    }

    public void setColor(float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        // GuiComponent keeps the stack (intersection, restore) but ignores the pose; HudGraphics promises it
        ScissorStack.Rect r = ScissorStack.transform(x1, y1, x2, y2, pose.last().pose().get(new float[16]));
        GuiComponent.enableScissor(Math.round(r.x1()), Math.round(r.y1()), Math.round(r.x2()), Math.round(r.y2()));
    }

    public void disableScissor() {
        GuiComponent.disableScissor();
    }

    /** 1.19.4 GUI drawing is immediate - nothing is batched. */
    public void flush() {
    }

    public void renderItem(ItemStack stack, int x, int y) {
        mc.getItemRenderer().renderAndDecorateItem(pose, stack, x, y);
    }

    /** Count and durability bar of an item at (x, y), like the hotbar. */
    public void renderItemDecorations(ItemStack stack, int x, int y) {
        mc.getItemRenderer().renderGuiItemDecorations(pose, mc.font, stack, x, y);
    }
}
