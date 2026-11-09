package dev.lumora.core.hud;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.FakeGraphics;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeystrokesViewTest {
    static final class Keys implements KeystrokesView.Input {
        final Set<KeystrokesView.Key> down = EnumSet.noneOf(KeystrokesView.Key.class);

        @Override
        public boolean down(KeystrokesView.Key key) {
            return down.contains(key);
        }

        @Override
        public String label(KeystrokesView.Key key) {
            return key.name().substring(0, 1);
        }

        @Override
        public int cps(boolean left) {
            return left ? 12 : 3;
        }
    }

    private static KeystrokesView view(SettingsGroup s) {
        KeystrokesView v = new KeystrokesView(0x9C15111D, 0xFF8B5CF6, 0xFFFFFFFF);
        v.configure(s);
        return v;
    }

    @Test
    void sizeFollowsTheChosenKeys() {
        SettingsGroup s = new SettingsGroup("lumora.widget.keystrokes.");
        KeystrokesView v = view(s);
        assertEquals(18 * 3 + 2 * 2, v.width());
        assertEquals(18 * 3 + 2 * 2 + 9 + 2, v.height(), "W, A S D, mouse buttons and jump bar");
        v.sneak.set(true);
        v.size.set(24);
        v.gap.set(0);
        assertEquals(72, v.width());
        assertEquals(24 * 3 + 9 + 9, v.height(), "jump and sneak bars as high as set (9 px)");
        v.spaceHeight.set(20);
        assertEquals(24 * 3 + 20 + 20, v.height());
        assertTrue(s.get("pressed").isPresent() && s.get("mouse").isPresent() && s.get("space").isPresent(),
                "the ids of earlier versions stay, so saved choices are kept");
    }

    @Test
    void mouseButtonsShowClicksPerSecond() {
        KeystrokesView v = view(new SettingsGroup("k."));
        FakeGraphics g = new FakeGraphics();
        v.render(g, new Keys(), true, 0xFFFFFFFF, 1000);
        assertTrue(g.texts.contains("LMB") && g.texts.contains("12 CPS") && g.texts.contains("3 CPS"), g.texts.toString());
        v.cps.set(false);
        g = new FakeGraphics();
        v.render(g, new Keys(), true, 0xFFFFFFFF, 1100);
        assertFalse(g.texts.contains("12 CPS"));
        assertEquals(0, g.depth);
    }

    @Test
    void keysFadeInOverTheChosenTime() {
        KeystrokesView v = view(new SettingsGroup("k."));
        v.fade.set(100);
        Keys keys = new Keys();
        v.render(new FakeGraphics(), keys, true, 0, 1000);
        keys.down.add(KeystrokesView.Key.UP);
        v.render(new FakeGraphics(), keys, true, 0, 1050);
        assertEquals(0xFF808080, KeystrokesView.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        // half the fade time: the key is half lit; after the full time it is fully lit
        assertEquals(KeystrokesView.mix(0x9C15111D, 0xD08B5CF6, 0.5f), firstKeyFill(v, keys, 1050));
        assertEquals(0xD08B5CF6, firstKeyFill(v, keys, 1100));
    }

    @Test
    void theLiquidBounceFillGrowsFromTheEdgesWithCssEase() {
        assertEquals(0f, KeystrokesView.ease(0f));
        assertEquals(1f, KeystrokesView.ease(1f));
        assertEquals(0.8f, KeystrokesView.ease(0.5f), 0.01f, "CSS ease is past 80 % at half the time");
        KeystrokesView v = view(new SettingsGroup("k."));
        v.liquidBounce();
        assertEquals("fill", v.animation.get());
        assertEquals(200, v.fade.get());
        assertFalse(v.mouse.get(), "LiquidBounce shows W A S D and space only");
        assertEquals(v.size.get(), v.spaceHeight.get(), "a full-height space key");
        assertEquals(KeystrokesView.LB_ACTIVE, v.pressed.get());
        v.rounded.set(false); // square keys: the fill is easy to check with plain rects
        Keys keys = new Keys();
        v.render(new FakeGraphics(), keys, true, 0, 1000);
        keys.down.add(KeystrokesView.Key.UP);
        FakeGraphics half = new FakeGraphics();
        v.render(half, keys, true, 0, 1100);
        // W key at x 20..38: pressed colour over the whole key, the key colour as a smaller hole in the middle
        assertTrue(half.rects.stream().anyMatch(r -> r[0] == 20 && r[1] == 0 && r[2] == 38 && r[3] == 18), "colour under the key");
        assertTrue(half.rects.stream().anyMatch(r -> r[0] > 20 && r[0] < 29 && r[1] > 0 && r[2] < 38), "the hole is still open at half the time");
        FakeGraphics full = new FakeGraphics();
        v.render(full, keys, true, 0, 1200);
        assertFalse(full.rects.stream().anyMatch(r -> r[0] > 20 && r[0] < 29 && r[1] > 0 && r[1] < 9 && r[2] < 38),
                "full after 200 ms");
        v.lumora();
        assertEquals("fade", v.animation.get());
        assertTrue(v.mouse.get());
    }

    @Test
    void theOutlineHasTheChosenWidth() {
        KeystrokesView v = view(new SettingsGroup("k."));
        v.outline.set(true);
        v.outlineWidth.set(3);
        FakeGraphics g = new FakeGraphics();
        v.render(g, new Keys(), true, 0, 1000);
        // the W key sits at x 20..38, y 0..18: its top border runs from y -3 to 0
        assertTrue(g.rects.stream().anyMatch(r -> r[0] == 17 && r[1] == -3 && r[2] == 41 && r[3] == 0),
                "3 px border above the W key");
    }

    /** Colour of the W key after a frame at {@code now} (its fill is the first one drawn). */
    private static int firstKeyFill(KeystrokesView v, KeystrokesView.Input in, long now) {
        int[] color = new int[1];
        dev.lumora.api.render.HudGraphics probe = new dev.lumora.api.render.HudGraphics() {
            boolean first = true;

            @Override public int textWidth(String text) { return text.length() * 6; }
            @Override public int lineHeight() { return 9; }
            @Override public void text(String text, int x, int y, int c, boolean shadow) { }
            @Override public void fill(int x1, int y1, int x2, int y2, int c) {
                if (first) {
                    color[0] = c;
                    first = false;
                }
            }
            @Override public void texture(dev.lumora.api.render.TextureRef t, int x, int y, int w, int h, float u, float vv, int rw, int rh, int tw, int th) { }
            @Override public void push() { }
            @Override public void pop() { }
            @Override public void translate(float x, float y) { }
            @Override public void scale(float x, float y) { }
            @Override public void enableScissor(int x1, int y1, int x2, int y2) { }
            @Override public void disableScissor() { }
            @Override public <T> java.util.Optional<T> unwrap(Class<T> type) { return java.util.Optional.empty(); }
        };
        v.render(probe, in, true, 0, now);
        return color[0];
    }
}
