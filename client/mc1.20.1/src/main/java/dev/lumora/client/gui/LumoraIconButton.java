package dev.lumora.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Square glass button with an icon (e.g. icon/language) - like the vanilla icon buttons. Minecraft 1.20.1 has no GUI
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
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        boolean hover = isHoveredOrFocused();
        if (hover) {
            LumoraShaders.glow(g, getX(), getY(), 20, 20, LumoraUi.ACCENT, 4);
        }
        LumoraShaders.glass(g, getX(), getY(), 20, 20, hover ? 0xE8263040 : 0xD8171C26);
        RenderSystem.enableBlend();
        g.blit(texture, getX() + 2, getY() + 2, 0, 0, 15, 15, 15, 15);
        RenderSystem.disableBlend();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
