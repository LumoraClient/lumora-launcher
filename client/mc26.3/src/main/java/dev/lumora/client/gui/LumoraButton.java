package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Button in the launcher's style: PRIMARY (accent), NAV (sidebar entry), SECONDARY (dark surface). */
public final class LumoraButton extends AbstractButton {
    public enum Style { PRIMARY, NAV, SECONDARY }

    private static final dev.lumora.client.api.McHudGraphics STYLE_G = new dev.lumora.client.api.McHudGraphics();
    private final Style style;
    private final Runnable action;
    private final String glyph;

    public LumoraButton(int x, int y, int w, int h, Component label, Style style, String glyph, Runnable action) {
        super(x, y, w, h, label);
        this.style = style;
        this.action = action;
        this.glyph = glyph;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        int textColor = active ? LumoraUi.TEXT : LumoraUi.MUTED;
        dev.lumora.core.extras.ButtonStyleModule buttonStyle = dev.lumora.core.extras.ButtonStyleModule.styled();
        if (buttonStyle != null && style != Style.NAV) {
            // button design chosen in the hub (Themes): Lumora's own buttons too
            buttonStyle.drawBackground(STYLE_G.with(g), this, x, y, w, h, hover, active);
            drawLabel(g, font, x, y, w, h, hover, dev.lumora.core.extras.ButtonStyleModule.textColor(active));
            return;
        }
        if (LumoraShaders.enabled()) {
            switch (style) {
                case PRIMARY -> {
                    LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, hover ? 8 : 4);
                    LumoraShaders.glass(g, x, y, w, h, !active ? 0xE0221C2D : hover ? LumoraUi.ACCENT_HOVER : LumoraUi.ACCENT);
                    textColor = 0xFFFFFFFF;
                }
                case NAV -> {
                    if (hover) {
                        LumoraShaders.glass(g, x, y, w, h, 0xC02B2438);
                        g.fill(x + 1, y + 5, x + 3, y + h - 5, LumoraUi.ACCENT);
                    }
                    textColor = hover ? LumoraUi.TEXT : active ? 0xFFCBC3DA : LumoraUi.MUTED;
                }
                case SECONDARY -> {
                    if (hover) {
                        LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, 4);
                    }
                    LumoraShaders.glass(g, x, y, w, h, hover ? 0xE82E2640 : 0xD81C1726);
                }
            }
            drawLabel(g, font, x, y, w, h, hover, textColor);
            return;
        }
        switch (style) {
            case PRIMARY -> {
                LumoraUi.panel(g, x, y + 2, w, h, LumoraUi.alpha(LumoraUi.ACCENT, hover ? 0x70 : 0x40));
                LumoraUi.panel(g, x, y, w, h, !active ? LumoraUi.SURFACE_2 : hover ? LumoraUi.ACCENT_HOVER : LumoraUi.ACCENT);
                g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, active ? LumoraUi.ACCENT_DARK : LumoraUi.SURFACE_2);
                textColor = 0xFFFFFFFF;
            }
            case NAV -> {
                if (hover) {
                    LumoraUi.panel(g, x, y, w, h, LumoraUi.SURFACE_2);
                    g.fill(x, y + 4, x + 2, y + h - 4, LumoraUi.ACCENT);
                }
                textColor = hover ? LumoraUi.TEXT : active ? 0xFFCBC3DA : LumoraUi.MUTED;
            }
            case SECONDARY -> LumoraUi.card(g, x, y, w, h, hover ? LumoraUi.SURFACE_2 : LumoraUi.SURFACE, hover ? LumoraUi.ACCENT : LumoraUi.BORDER);
        }
        drawLabel(g, font, x, y, w, h, hover, textColor);
    }

    private void drawLabel(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, boolean hover, int textColor) {
        String label = getMessage().getString();
        int ty = y + (h - font.lineHeight) / 2 + 1;
        if (style == Style.NAV) {
            int tx = x + 10;
            if (glyph != null) {
                g.text(font, glyph, tx, ty, hover ? LumoraUi.ACCENT_HOVER : LumoraUi.ACCENT, false);
                tx += 14;
            }
            g.text(font, label, tx, ty, textColor, false);
        } else {
            String full = glyph != null ? glyph + "  " + label : label;
            g.centeredText(font, full, x + w / 2, ty, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
