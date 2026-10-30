package dev.lumora.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
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
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
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

    // --- textures: 1.20 binds the texture per call, 1.18.2 through the shader texture slot

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

    /** GUI coordinates; 1.18.2 has no GuiComponent#enableScissor, so they are converted to window pixels here. */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        var window = mc.getWindow();
        double scale = window.getGuiScale();
        RenderSystem.enableScissor((int) (x1 * scale), (int) (window.getHeight() - y2 * scale),
                Math.max(0, (int) ((x2 - x1) * scale)), Math.max(0, (int) ((y2 - y1) * scale)));
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    /** 1.18.2 GUI drawing is immediate - nothing is batched. */
    public void flush() {
    }

    /** 1.18.2 renders GUI items through the global model-view stack, so this frame's pose is applied to it. */
    public void renderItem(ItemStack stack, int x, int y) {
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        mc.getItemRenderer().renderAndDecorateItem(stack, x, y);
        view.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
