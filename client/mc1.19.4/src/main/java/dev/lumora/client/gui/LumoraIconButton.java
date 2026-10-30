package dev.lumora.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Square glass button with an icon (e.g. icon/language) - like the vanilla icon buttons. Minecraft 1.19.4 has no GUI
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
    public void renderWidget(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.of(pose);
        boolean hover = isHoveredOrFocused();
        if (hover) {
            LumoraShaders.glow(g, getX(), getY(), 20, 20, LumoraUi.ACCENT, 4);
        }
        LumoraShaders.glass(g, getX(), getY(), 20, 20, hover ? 0xE82E2640 : 0xD81C1726);
        RenderSystem.enableBlend();
        g.blit(texture, getX() + 2, getY() + 2, 0, 0, 15, 15, 15, 15);
        RenderSystem.disableBlend();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
