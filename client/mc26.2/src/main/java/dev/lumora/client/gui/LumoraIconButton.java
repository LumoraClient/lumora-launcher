package dev.lumora.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Square glass button showing a vanilla GUI sprite (e.g. icon/language) - like the vanilla icon buttons. */
public final class LumoraIconButton extends AbstractButton {
    private final Identifier sprite;
    private final Runnable action;

    public LumoraIconButton(int x, int y, Component label, Identifier sprite, Runnable action) {
        super(x, y, 20, 20, label);
        this.sprite = sprite;
        this.action = action;
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean hover = isHoveredOrFocused();
        if (hover) {
            LumoraShaders.glow(g, getX(), getY(), 20, 20, LumoraUi.ACCENT, 4);
        }
        LumoraShaders.glass(g, getX(), getY(), 20, 20, hover ? 0xE82E2640 : 0xD81C1726);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX() + 2, getY() + 2, 15, 15);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
