package dev.lumora.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Square glass button showing a vanilla GUI sprite (e.g. icon/language) - like the vanilla icon buttons. */
public final class LumoraIconButton extends AbstractButton {
    private final ResourceLocation sprite;
    private final Runnable action;

    public LumoraIconButton(int x, int y, Component label, ResourceLocation sprite, Runnable action) {
        super(x, y, 20, 20, label);
        this.sprite = sprite;
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
        LumoraShaders.glass(g, getX(), getY(), 20, 20, hover ? 0xE82E2640 : 0xD81C1726);
        g.blitSprite(sprite, getX() + 2, getY() + 2, 15, 15);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
