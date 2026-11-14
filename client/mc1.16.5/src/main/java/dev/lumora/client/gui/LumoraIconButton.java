package dev.lumora.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Square glass button with an icon (e.g. icon/language) - like the vanilla icon buttons. Minecraft 1.16.5 has no GUI
 * sprite atlas yet, so the icon is Lumora's own texture assets/lumora/textures/gui/<sprite path>.png (15x15).
 */
public final class LumoraIconButton extends AbstractButton {
    private final ResourceLocation sprite;
    private final ResourceLocation texture;
    private final Runnable action;

    public LumoraIconButton(int x, int y, Component label, ResourceLocation sprite, Runnable action) {
        super(x, y, 20, 20, label);
        this.sprite = sprite;
        this.texture = new ResourceLocation("lumora", "textures/gui/" + sprite.getPath() + ".png");
        this.action = action;
    }

    /** Minecraft 1.16.5 has no widget Tooltip class: AbstractWidget#render calls this while hovered. */
    @Override
    public void renderToolTip(PoseStack pose, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.renderTooltip(pose, getMessage(), mouseX, mouseY);
        }
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.of(pose);
        boolean hover = isHovered();
        if (hover) {
            LumoraShaders.glow(g, x, y, 20, 20, LumoraUi.ACCENT, 4);
        }
        LumoraShaders.glass(g, x, y, 20, 20, hover ? 0xE8263040 : 0xD8171C26);
        RenderSystem.enableBlend();
        g.blit(texture, x + 2, y + 2, 0, 0, 15, 15, 15, 15);
        RenderSystem.disableBlend();
    }

}
