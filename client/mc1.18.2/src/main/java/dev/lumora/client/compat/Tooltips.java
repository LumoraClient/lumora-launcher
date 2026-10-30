package dev.lumora.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Widget tooltips for Minecraft 1.18.2 and older, which have no {@code Tooltip} class (1.19.3+): a screen registers
 * the text per widget while building its widgets and draws the tooltip of the hovered one after everything else,
 * with the same wrapping width as vanilla tooltips.
 */
public final class Tooltips {
    private static final int WIDTH = 200;
    private final Map<AbstractWidget, Component> texts = new IdentityHashMap<>();

    /** Call at the start of {@code init()}: widgets are rebuilt there. */
    public void clear() {
        texts.clear();
    }

    public void set(AbstractWidget widget, Component text) {
        texts.put(widget, text);
    }

    /** Draws the tooltip of the widget under the mouse, if it has one. */
    public void render(Screen screen, PoseStack pose, int mouseX, int mouseY) {
        for (Map.Entry<AbstractWidget, Component> e : texts.entrySet()) {
            AbstractWidget w = e.getKey();
            if (w.visible && w.isMouseOver(mouseX, mouseY)) {
                screen.renderTooltip(pose, Minecraft.getInstance().font.split(e.getValue(), WIDTH), mouseX, mouseY);
                return;
            }
        }
    }
}
