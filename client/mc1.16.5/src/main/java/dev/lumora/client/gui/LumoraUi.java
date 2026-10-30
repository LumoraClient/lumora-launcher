package dev.lumora.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * In-game version of the launcher's design tokens (same colors as dev.lumora.launcher.app.ui.Theme) and a few
 * drawing helpers on top of the 1.21.8 GuiGraphics API.
 */
public final class LumoraUi {
    public static final int BG = 0xFF0F0C17;
    public static final int SURFACE = 0xFF17131F;
    public static final int SURFACE_2 = 0xFF221C2D;
    public static final int BORDER = 0xFF2F2741;
    public static final int TEXT = 0xFFECE8F4;
    public static final int MUTED = 0xFF9D95AE;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_HOVER = 0xFFA27BFA;
    public static final int ACCENT_DARK = 0xFF7243E0;
    public static final int OK = 0xFF3DD68C;
    public static final int WARN = 0xFFF5B342;

    public static final ResourceLocation BACKGROUND = new ResourceLocation("lumora", "textures/gui/background.png");
    public static final ResourceLocation LOGO = new ResourceLocation("lumora", "icon.png");
    private static final int BG_W = 1920;
    private static final int BG_H = 1080;

    private LumoraUi() {
    }

    public static int alpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Same landscape without the moon - used behind blurred menus, where the moon would turn into a grey box. */
    public static final ResourceLocation BACKGROUND_MENU = new ResourceLocation("lumora", "textures/gui/background_menu.png");

    /** The launcher artwork (main menu variant with moon), scaled to cover the screen. */
    public static void background(GuiGraphics g, int w, int h) {
        background(g, w, h, BACKGROUND);
    }

    /** Draws one of the 1920x1080 landscape variants, scaled to cover the screen (center crop). */
    public static void background(GuiGraphics g, int w, int h, ResourceLocation texture) {
        float screen = w / (float) h;
        float tex = BG_W / (float) BG_H;
        int rw = BG_W;
        int rh = BG_H;
        if (screen > tex) {
            rh = Math.round(BG_W / screen);
        } else {
            rw = Math.round(BG_H * screen);
        }
        int u = (BG_W - rw) / 2;
        int v = (BG_H - rh) / 2;
        g.blit(texture, 0, 0, w, h, u, v, rw, rh, BG_W, BG_H);
    }

    /** Filled rectangle with 2px cut corners - reads as "rounded" at GUI scale. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x, y + 2, x + 2, y + h - 2, color);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** Card: panel plus a 1px outline. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int outline) {
        panel(g, x, y, w, h, outline);
        panel(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** Built-in "Lumora Theme" pack (registered in LumoraClient). */
    private static final String THEME_PACK_ID = "lumora:lumora_theme";
    private static long themeCheckedAt;
    private static boolean themeOn = true;

    /**
     * Minecraft 1.16.5: vanilla buttons and sliders get the Lumora look (ButtonSkinMixin, SliderSkinMixin) while the
     * "Lumora Theme" pack is selected - the pack itself cannot re-skin widgets.png buttons in this version.
     * Checked at most every 250 ms (called for every button each frame).
     */
    public static boolean vanillaSkin() {
        long now = System.nanoTime();
        if (now - themeCheckedAt > 250_000_000L) {
            themeCheckedAt = now;
            themeOn = Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(THEME_PACK_ID);
        }
        return themeOn;
    }

    /** A vanilla button background in the Lumora style (matches the theme pack sprites of 1.20.2+). */
    public static void vanillaButton(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hover) {
        int fill = !active ? 0xB015111D : hover ? 0xF02E2645 : 0xDC1C1726;
        card(g, x, y, w, h, fill, hover ? alpha(ACCENT, 0x90) : BORDER);
        if (hover) {
            g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, alpha(ACCENT, 0xC0));
        }
    }

    public static void logo(GuiGraphics g, int x, int y, int size) {
        g.blit(LOGO, x, y, size, size, 0, 0, 128, 128, 128, 128);
    }

    /** Text scaled up around its top-left corner (Minecraft's font has a single size). */
    public static void bigText(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }

    /** Small rounded label like the launcher's chips. */
    public static int chip(GuiGraphics g, String text, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int w = font.width(text) + 10;
        panel(g, x, y, w, 13, alpha(ACCENT, 0x55));
        g.drawString(font, text, x + 5, y + 3, 0xFFDCCCFD, false);
        return w;
    }

    /** "Label / value" pair used in cards. */
    public static void stat(GuiGraphics g, String label, String value, int valueColor, int x, int y, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, label.toUpperCase(), x, y, MUTED, false);
        String v = value;
        while (font.width(v) > maxWidth && v.length() > 4) {
            v = v.substring(0, v.length() - 2);
        }
        if (!v.equals(value)) {
            v = v + "…";
        }
        g.drawString(font, v, x, y + 12, valueColor, false);
    }
}
