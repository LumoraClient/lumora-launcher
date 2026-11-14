package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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
    public void onPress() {
        action.run();
    }

    @Override
    public void renderWidget(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuiGraphics g = GuiGraphics.of(pose);
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
                    LumoraShaders.glass(g, x, y, w, h, !active ? 0xE016213A : hover ? LumoraUi.ACCENT_HOVER : LumoraUi.ACCENT);
                    textColor = 0xFFFFFFFF;
                }
                case NAV -> {
                    if (hover) {
                        LumoraShaders.glass(g, x, y, w, h, 0xC01C2A47);
                        g.fill(x + 1, y + 5, x + 3, y + h - 5, LumoraUi.ACCENT);
                    }
                    textColor = hover ? LumoraUi.TEXT : active ? 0xFFCBC3DA : LumoraUi.MUTED;
                }
                case SECONDARY -> {
                    if (hover) {
                        LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, 4);
                    }
                    LumoraShaders.glass(g, x, y, w, h, hover ? 0xE8263040 : 0xD8171C26);
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

    private void drawLabel(GuiGraphics g, Font font, int x, int y, int w, int h, boolean hover, int textColor) {
        if (glyph != null && glyph.startsWith("#") && dev.lumora.core.hub.HubGlyphs.has(glyph.substring(1))) {
            // a pixel glyph of the hub (e.g. the camera), centred; the label is only read out
            dev.lumora.core.hub.HubGlyphs.draw(STYLE_G.with(g), glyph.substring(1), x + (w - dev.lumora.core.hub.HubGlyphs.SIZE) / 2,
                    y + (h - dev.lumora.core.hub.HubGlyphs.SIZE) / 2, hover ? LumoraUi.ACCENT_HOVER : textColor);
            return;
        }
        String label = getMessage().getString();
        int ty = y + (h - font.lineHeight) / 2 + 1;
        if (style == Style.NAV) {
            int tx = x + 10;
            if (glyph != null) {
                g.drawString(font, glyph, tx, ty, hover ? LumoraUi.ACCENT_HOVER : LumoraUi.ACCENT, false);
                tx += 14;
            }
            g.drawString(font, label, tx, ty, textColor, false);
        } else {
            String full = glyph != null ? glyph + "  " + label : label;
            g.drawCenteredString(font, full, x + w / 2, ty, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
