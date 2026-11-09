package dev.lumora.core.hud;

import dev.lumora.api.config.BooleanSetting;
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

    /** Fade state per key: 0 released, 1 pressed. */
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
        fade = s.slider("fade", 100, 0, 400).step(25).unit(" ms");
        rounded = s.toggle("rounded", false);
        outline = s.toggle("outline", false);
        outlineWidth = s.slider("outline_width", 1, 1, 3).unit(" px").requires(outline);
        outlineColor = s.color("outline_color", 0xFFFFFFFF, true).requires(outline);
        shadow = s.toggle("shadow", true);
        keyColor = s.color("key_color", defaultKey, true).section(k + "section.colors");
        pressed = s.chroma("pressed", defaultPressed, false);
        pressedText = s.chroma("pressed_text", 0xFF0E0A16, false);
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
        return Math.max(9, k() / 2);
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
            key(g, 0, y, full, bar(), "———", null, Key.JUMP, background, text, now);
            y += bar() + gp;
        }
        if (sneak.get()) {
            key(g, 0, y, full, bar(), "Shift", null, Key.SNEAK, background, text, now);
        }
    }

    private void key(HudGraphics g, int x, int y, int w, int h, String label, String sub, Key key, boolean background,
                     int text, long now) {
        float t = glow[key.ordinal()];
        int base = keyColor.argb();
        if (!background) {
            base = Colors.withAlpha(base, (base >>> 24) / 2);
        }
        int fill = mix(base, Colors.withAlpha(pressed.argbAt(now), 0xD0), t);
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
        int color = mix(text, pressedText.argbAt(now), t);
        boolean shade = shadow.get() && t < 0.5f;
        if (sub != null && h >= 16) {
            g.centeredText(label, x + w / 2, y + h / 2 - 7, color, shade);
            g.push();
            g.translate(x + w / 2f, y + h / 2f + 2);
            g.scale(0.5f);
            g.centeredText(sub, 0, 0, color, shade);
            g.pop();
        } else {
            g.centeredText(label, x + w / 2, y + (h - 8) / 2, color, shade);
        }
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
