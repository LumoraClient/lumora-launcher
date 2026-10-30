package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.HudGraphics;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Built-in module "Button-Design": the background of every Minecraft button in one of the styles of FDPClient's button
 * renderers (Blackout, Hyperium, Liquid, Lunar, PvPClient, Rounded; FDPClient is GPL-3.0 like Lumora, re-implemented
 * here on HudGraphics) or Lumora's own look ("lumora", the default: nothing changes). The version adapters ask
 * {@link #styled()} where Minecraft draws a button and call {@link #drawBackground}; the label stays Minecraft's.
 */
public final class ButtonStyleModule implements LumoraAddon {
    public static final String LUMORA = "lumora";
    private static volatile ButtonStyleModule active;

    ChoiceSetting style;
    BooleanSetting shadow;
    /** Per-button animation state (Blackout alpha, Liquid inset, Lunar alpha), dropped with the button. */
    private final Map<Object, float[]> anim = new WeakHashMap<>();

    public static ButtonStyleModule active() {
        return active;
    }

    /** The module when a style other than Lumora's own is chosen, else null. */
    public static ButtonStyleModule styled() {
        ButtonStyleModule m = active;
        return m != null && !LUMORA.equals(m.style.get()) ? m : null;
    }

    @Override
    public void onInitialize(AddonContext context) {
        SettingsGroup s = context.configuration().settings();
        style = (ChoiceSetting) s.choice("style", LUMORA, LUMORA, "blackout", "hyperium", "liquid", "lunar", "pvpclient", "rounded")
                .name("lumora.extras.buttons.style");
        shadow = (BooleanSetting) s.toggle("shadow", true).name("lumora.extras.buttons.shadow");
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        active = null;
    }

    public String style() {
        return style.get();
    }

    /** Label colour for the styles: white, grey when the button is inactive. */
    public static int textColor(boolean enabled) {
        return enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
    }

    /**
     * Draws the button background.
     *
     * @param button the Minecraft button (key for its animation state)
     */
    public synchronized void drawBackground(HudGraphics g, Object button, int x, int y, int w, int h, boolean hover, boolean enabled) {
        float[] st = anim.computeIfAbsent(button, k -> new float[]{100, w / 2f, 40});
        boolean hovered = hover && enabled;
        switch (style.get()) {
            case "blackout" -> {
                st[0] = ease(st[0], hovered ? 50 : 100, 0.12f);
                shadow(g, x, y, w, h, 0);
                g.fill(x, y, x + w, y + h, a((int) st[0], 0x000000));
            }
            case "hyperium" -> {
                shadow(g, x, y, w, h, 0);
                g.fill(x, y, x + w, y + h, a(hovered ? 50 : 100, 0x000000));
            }
            case "liquid" -> {
                st[1] = ease(st[1], hovered ? w / 15f : w / 2f, 0.12f);
                int inset = Math.round(st[1]);
                int alpha = (int) Math.min(200, 120 + st[1] * 4);
                if (inset * 2 < w) {
                    g.fill(x + inset, y, x + w - inset, y + h, a(alpha, 0x000000));
                }
            }
            case "lunar" -> {
                st[2] = ease(st[2], hovered ? 70 : 40, 0.04f);
                g.roundedRect(x - 1, y - 1, w + 2, h + 2, 5, a(50, 0x000000));
                g.roundedRect(x, y, w, h, 4, a(80, 0xFFFFFF));
                g.roundedRect(x + 2, y + 2, w - 4, h - 4, 2, a((int) st[2], 0xFFFFFF));
            }
            case "pvpclient" -> {
                g.outline(x - 1, y - 1, w + 2, h + 2, a(79, 0x8C8C8C));
                g.fill(x, y, x + w, y + h, a(hovered ? 180 : 128, 0x000000));
            }
            case "rounded" -> {
                int r = (int) Math.round(Math.sqrt((double) w * h) * 0.1);
                shadow(g, x, y, w, h, r);
                g.roundedRect(x, y, w, h, r, hovered ? a(150, 0x3C3C3C) : a(150, 0x1F1F1F));
            }
            default -> {
                // Lumora: adapters never call this for the default style
            }
        }
        if (!enabled) {
            g.fill(x, y, x + w, y + h, 0x30000000);
        }
    }

    private void shadow(HudGraphics g, int x, int y, int w, int h, int radius) {
        if (shadow.get()) {
            g.roundedRect(x + 1, y + 2, w, h, radius, 0x50000000);
        }
    }

    /** Frame-based easing like the reference: max(min step, |diff| * factor) towards the target. */
    static float ease(float now, float target, float factor) {
        float diff = target - now;
        if (Math.abs(diff) < 0.1f) {
            return target;
        }
        float step = Math.max(0.1f, Math.abs(diff) * factor);
        return now + Math.signum(diff) * Math.min(step, Math.abs(diff));
    }

    private static int a(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
