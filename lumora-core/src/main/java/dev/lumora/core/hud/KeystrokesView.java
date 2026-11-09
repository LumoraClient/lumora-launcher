package dev.lumora.core.hud;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.Colors;
import dev.lumora.api.render.HudGraphics;

/**
 * The keystrokes widget (W A S D, mouse buttons, jump, sneak), version-independent, with the options of Badlion's
 * keystrokes: clicks per second on the mouse buttons, key size and gap, fade time, rounded keys, outline, colours for
 * keys and text (rainbow possible), text shadow. Each Minecraft version only reports which keys are down.
 */
public final class KeystrokesView {
    /** The keys, in drawing order. */
    public enum Key { UP, LEFT, DOWN, RIGHT, ATTACK, USE, JUMP, SNEAK }

    /** What the game reports. */
    public interface Input {
        boolean down(Key key);

        /** Label of a movement key as bound in the controls (e.g. "W", or "Z" on AZERTY). */
        String label(Key key);

        /** Clicks per second of the attack (left) or use (right) button. */
        int cps(boolean left);
    }

    private final int defaultKey;
    private final int defaultPressed;
    private final int defaultText;

    public BooleanSetting mouse;
    public BooleanSetting cps;
    public BooleanSetting space;
    public BooleanSetting sneak;
    public IntegerSetting size;
    public IntegerSetting gap;
    public IntegerSetting fade;
    public BooleanSetting rounded;
    public BooleanSetting outline;
    public IntegerSetting outlineWidth;
    public ColorSetting outlineColor;
    public ColorSetting keyColor;
    public ColorSetting pressed;
    public ColorSetting pressedText;
    public BooleanSetting shadow;
    /** "fade": colour fades over (Badlion), "fill": the colour grows from the edges inwards (LiquidBounce). */
    public ChoiceSetting animation;
    /** Height of the jump and sneak bars (Badlion "space thickness"). */
    public IntegerSetting spaceHeight;
    /** Label size in percent (Badlion "text size"). */
    public IntegerSetting textSize;

    /** LiquidBounce's keystrokes: dark keys (black, 68 %), white text, blue fill growing in over 200 ms. */
    static final int LB_KEY = 0xAD000000;
    static final int LB_ACTIVE = 0xFF4677FF;

    /** Animation state per key: 0 released, 1 pressed (linear in time). */
    private final float[] glow = new float[Key.values().length];
    private long lastFrame;

    /**
     * @param keyColor     background of a released key (with alpha)
     * @param pressedColor background of a pressed key
     * @param textColor    label colour of a released key (the widget's value colour is used instead when given)
     */
    public KeystrokesView(int keyColor, int pressedColor, int textColor) {
        this.defaultKey = keyColor;
        this.defaultPressed = pressedColor;
        this.defaultText = textColor;
    }

    /** The widget's settings; the ids "mouse", "space" and "pressed" are the ones of earlier versions. */
    public void configure(SettingsGroup s) {
        String k = "lumora.widget.keystrokes.";
        mouse = s.toggle("mouse", true).section(k + "section.keys");
        cps = s.checkbox("cps", true).requires(mouse);
        space = s.toggle("space", true);
        sneak = s.toggle("sneak", false);
        size = s.slider("size", 18, 14, 28).unit(" px").section(k + "section.look");
        gap = s.slider("gap", 2, 0, 6).unit(" px");
        spaceHeight = s.slider("space_height", 9, 4, 28).unit(" px");
        textSize = s.slider("text_size", 100, 50, 150).step(10).unit(" %");
        rounded = s.toggle("rounded", false);
        outline = s.toggle("outline", false);
        outlineWidth = s.slider("outline_width", 1, 1, 3).unit(" px").requires(outline);
        outlineColor = s.color("outline_color", 0xFFFFFFFF, true).requires(outline);
        shadow = s.toggle("shadow", true);
        animation = s.selection("animation", "fade", "fade", "fill").section(k + "section.animation");
        fade = s.slider("fade", 100, 0, 400).step(25).unit(" ms");
        keyColor = s.color("key_color", defaultKey, true).section(k + "section.colors");
        pressed = s.chroma("pressed", defaultPressed, false);
        pressedText = s.chroma("pressed_text", 0xFF0E0A16, false);
        s.button("preset_liquidbounce", this::liquidBounce).label(k + "apply").section(k + "section.presets");
        s.button("preset_lumora", this::lumora).label(k + "apply");
    }

    /** LiquidBounce's look: W A S D and a full-height space key, rounded, blue fill animation (200 ms, ease). */
    public void liquidBounce() {
        mouse.set(false);
        sneak.set(false);
        space.set(true);
        gap.set(Math.max(1, Math.round(size.get() / 10f)));
        spaceHeight.set(size.get());
        rounded.set(true);
        outline.set(false);
        shadow.set(false);
        animation.select("fill");
        fade.set(200);
        keyColor.set(LB_KEY);
        pressed.setChroma(false);
        pressedText.setChroma(false);
        pressed.set(LB_ACTIVE);
        pressedText.set(0xFFFFFFFF);
    }

    /** Back to Lumora's own look (the defaults). */
    public void lumora() {
        mouse.set(true);
        cps.set(true);
        sneak.set(false);
        space.set(true);
        size.set(18);
        gap.set(2);
        spaceHeight.set(9);
        textSize.set(100);
        rounded.set(false);
        outline.set(false);
        shadow.set(true);
        animation.select("fade");
        fade.set(100);
        keyColor.set(defaultKey);
        pressed.setChroma(false);
        pressedText.setChroma(false);
        pressed.set(defaultPressed);
        pressedText.set(0xFF0E0A16);
    }

    private int k() {
        return size == null ? 18 : size.get();
    }

    private int g() {
        return gap == null ? 2 : gap.get();
    }

    public int width() {
        return k() * 3 + g() * 2;
    }

    public int height() {
        int k = k();
        int g = g();
        int h = k * 2 + g;
        if (mouse.get()) {
            h += k + g;
        }
        if (space.get()) {
            h += bar() + g;
        }
        if (sneak.get()) {
            h += bar() + g;
        }
        return h;
    }

    /** Height of the jump and sneak bars. */
    private int bar() {
        return spaceHeight == null ? 9 : spaceHeight.get();
    }

    /**
     * Draws the keys at (0, 0).
     *
     * @param background the widget's background switch (off: lighter keys)
     * @param textColor  label colour of released keys (the widget's value colour)
     */
    public void render(HudGraphics g, Input in, boolean background, int textColor, long now) {
        float dt = lastFrame == 0 ? 1000 : Math.min(1000, now - lastFrame);
        lastFrame = now;
        float step = fade.get() <= 0 ? 1f : dt / fade.get();
        for (Key key : Key.values()) {
            int i = key.ordinal();
            glow[i] = in.down(key) ? Math.min(1f, glow[i] + step) : Math.max(0f, glow[i] - step);
        }
        int k = k();
        int gp = g();
        int full = k * 3 + gp * 2;
        int text = textColor == 0 ? defaultText : textColor;
        key(g, k + gp, 0, k, k, in.label(Key.UP), null, Key.UP, background, text, now);
        key(g, 0, k + gp, k, k, in.label(Key.LEFT), null, Key.LEFT, background, text, now);
        key(g, k + gp, k + gp, k, k, in.label(Key.DOWN), null, Key.DOWN, background, text, now);
        key(g, (k + gp) * 2, k + gp, k, k, in.label(Key.RIGHT), null, Key.RIGHT, background, text, now);
        int y = (k + gp) * 2;
        if (mouse.get()) {
            int half = (full - gp) / 2;
            key(g, 0, y, half, k, "LMB", cps.get() ? in.cps(true) + " CPS" : null, Key.ATTACK, background, text, now);
            key(g, half + gp, y, full - half - gp, k, "RMB", cps.get() ? in.cps(false) + " CPS" : null, Key.USE, background, text, now);
            y += k + gp;
        }
        if (space.get()) {
            key(g, 0, y, full, bar(), bar() >= 14 ? "Space" : "———", null, Key.JUMP, background, text, now);
            y += bar() + gp;
        }
        if (sneak.get()) {
            key(g, 0, y, full, bar(), "Shift", null, Key.SNEAK, background, text, now);
        }
    }

    private void key(HudGraphics g, int x, int y, int w, int h, String label, String sub, Key key, boolean background,
                     int text, long now) {
        boolean grow = "fill".equals(animation.get());
        float t = grow ? ease(glow[key.ordinal()]) : glow[key.ordinal()];
        int base = keyColor.argb();
        if (!background) {
            base = Colors.withAlpha(base, (base >>> 24) / 2);
        }
        int active = Colors.withAlpha(pressed.argbAt(now), grow ? 0xFF : 0xD0);
        int fill = grow ? base : mix(base, active, t);
        int r = rounded.get() ? Math.min(4, h / 3) : 0;
        if (outline.get()) {
            int oc = outlineColor.argb();
            int n = outlineWidth.get();
            if (r > 0) {
                g.roundedRect(x - n, y - n, w + 2 * n, h + 2 * n, r + n, oc);
            } else {
                g.fill(x - n, y - n, x + w + n, y, oc);
                g.fill(x - n, y + h, x + w + n, y + h + n, oc);
                g.fill(x - n, y, x, y + h, oc);
                g.fill(x + w, y, x + w + n, y + h, oc);
            }
        }
        if (r > 0) {
            g.roundedRect(x, y, w, h, r, fill);
        } else {
            g.fill(x, y, x + w, y + h, fill);
        }
        if (grow && t > 0) {
            // LiquidBounce: an inner frame of the pressed colour that grows until the key is full
            int d = Math.round(t * (Math.min(w, h) / 2f + 1));
            if (r > 0) {
                g.roundedRect(x, y, w, h, r, active);
                if (w - 2 * d > 0 && h - 2 * d > 0) {
                    g.roundedRect(x + d, y + d, w - 2 * d, h - 2 * d, Math.max(0, r - d), base);
                }
            } else {
                g.fill(x, y, x + w, y + h, active);
                if (w - 2 * d > 0 && h - 2 * d > 0) {
                    g.fill(x + d, y + d, x + w - d, y + h - d, base);
                }
            }
        }
        // fill: the label keeps its colour until the fill reaches the middle (it sits on the key colour until then)
        float tt = grow ? Math.max(0f, (t - 0.75f) / 0.25f) : t;
        int color = mix(text, pressedText.argbAt(now), tt);
        boolean shade = shadow.get() && tt < 0.5f;
        float ts = textSize.get() / 100f;
        if (sub != null && h >= 16) {
            label(g, label, x + w / 2f, y + h / 2f - 7 * ts, ts, color, shade);
            label(g, sub, x + w / 2f, y + h / 2f + 2, 0.5f * ts, color, shade);
        } else {
            label(g, label, x + w / 2f, y + h / 2f - 4 * ts, ts, color, shade);
        }
    }

    /** Text centred on {@code cx}, top at {@code top}, at {@code scale}. */
    private static void label(HudGraphics g, String text, float cx, float top, float scale, int color, boolean shadow) {
        if (scale == 1f) {
            g.centeredText(text, Math.round(cx), Math.round(top), color, shadow);
            return;
        }
        g.push();
        g.translate(cx, top);
        g.scale(scale);
        g.centeredText(text, 0, 0, color, shadow);
        g.pop();
    }

    /** CSS "ease" (cubic-bezier(.25, .1, .25, 1)), as LiquidBounce's keys use it. */
    static float ease(float x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        // find the curve parameter u for x, then return its y
        float u = x;
        for (int i = 0; i < 8; i++) {
            float bx = bezier(u, 0.25f, 0.25f) - x;
            float dx = bezierSlope(u, 0.25f, 0.25f);
            if (Math.abs(bx) < 1e-4f || dx == 0) {
                break;
            }
            u = Math.max(0, Math.min(1, u - bx / dx));
        }
        return bezier(u, 0.1f, 1f);
    }

    private static float bezier(float u, float p1, float p2) {
        float v = 1 - u;
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
    }

    private static float bezierSlope(float u, float p1, float p2) {
        float v = 1 - u;
        return 3 * v * v * p1 + 6 * v * u * (p2 - p1) + 3 * u * u * (1 - p2);
    }

    /** Linear mix of two ARGB colours (alpha too). */
    static int mix(int a, int b, float t) {
        if (t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = a >>> shift & 0xFF;
            int cb = b >>> shift & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
