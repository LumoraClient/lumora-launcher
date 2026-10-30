package dev.lumora.client.gui;

import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.compat.AbstractButton;
import dev.lumora.client.compat.Tooltip;
import dev.lumora.client.compat.Component;
import net.minecraft.util.Identifier;

/**
 * Square glass button with an icon (e.g. icon/language) - like the vanilla icon buttons. Minecraft 1.12.2 has no GUI
 * sprite atlas yet, so the icon is Lumora's own texture assets/lumora/textures/gui/<sprite path>.png (15x15).
 */
public final class LumoraIconButton extends AbstractButton {
    private final Identifier sprite;
    private final Identifier texture;
    private final Runnable action;

    public LumoraIconButton(int x, int y, Component label, Identifier sprite, Runnable action) {
        super(x, y, 20, 20, label);
        this.sprite = sprite;
        this.texture = new Identifier("lumora", "textures/gui/" + sprite.getPath() + ".png");
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
        g.blit(texture, getX() + 2, getY() + 2, 0, 0, 15, 15, 15, 15);
    }

}
