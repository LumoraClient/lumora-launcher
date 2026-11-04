package dev.lumora.core.extras;

import dev.lumora.api.render.HudGraphics;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrosshairPresetsTest {
    /** Records every filled pixel and how often it was filled. */
    static final class Pixels implements HudGraphics {
        final Map<String, Integer> color = new HashMap<>();
        final Map<String, Integer> count = new HashMap<>();

        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public int lineHeight() { return 9; }
        @Override public void text(String text, int x, int y, int c, boolean shadow) { }
        @Override public void fill(int x1, int y1, int x2, int y2, int c) {
            for (int y = y1; y < y2; y++) {
                for (int x = x1; x < x2; x++) {
                    color.put(x + "," + y, c);
                    count.merge(x + "," + y, 1, Integer::sum);
                }
            }
        }
        @Override public void texture(dev.lumora.api.render.TextureRef t, int x, int y, int w, int h, float u, float v,
                                      int rw, int rh, int tw, int th) { }
        @Override public void push() { }
        @Override public void pop() { }
        @Override public void translate(float x, float y) { }
        @Override public void scale(float x, float y) { }
        @Override public void enableScissor(int x1, int y1, int x2, int y2) { }
        @Override public void disableScissor() { }
        @Override public <T> Optional<T> unwrap(Class<T> type) { return Optional.empty(); }
    }

    @Test
    void presetsAreUniqueCentredAndSurviveStorage() {
        Set<String> ids = new HashSet<>();
        int n = CrosshairPresets.GRID;
        for (CrosshairPresets.Preset p : CrosshairPresets.all()) {
            assertTrue(ids.add(p.id()), "unique id " + p.id());
            assertEquals(n * n, p.pixels().length);
            int count = 0;
            for (int i = 0; i < p.pixels().length; i++) {
                if (p.pixels()[i]) {
                    count++;
                    // every preset is symmetric left/right, so it sits in the middle
                    int x = i % n;
                    int y = i / n;
                    assertTrue(p.pixels()[y * n + (n - 1 - x)], p.id() + " symmetric at " + x + "," + y);
                }
            }
            assertTrue(count > 0, p.id());
            assertArrayEquals(p.pixels(), CrosshairPresets.decode(CrosshairPresets.encode(p.pixels())), p.id());
        }
        assertTrue(CrosshairPresets.all().size() >= 24);
        assertNull(CrosshairPresets.decode("kaputt"));
        assertNull(CrosshairPresets.decode("zz".repeat(30)));
        assertEquals(CrosshairPresets.all().get(0), CrosshairPresets.get("gibt-es-nicht"));
        assertThrows(IllegalArgumentException.class, () -> CrosshairPresets.of("even", "", "", "##", "##"));
        assertThrows(IllegalArgumentException.class, () -> CrosshairPresets.of("ragged", "", "", "#..", "#"));
    }

    @Test
    void bitmapDrawsEveryPixelOnceWithAnEvenOutline() {
        boolean[] px = CrosshairPresets.get("plus").pixels();
        Pixels g = new Pixels();
        CrosshairModule.bitmap(g, px, 0xFFFFFFFF, 0xB0000000);
        int n = CrosshairPresets.GRID;
        int white = 0;
        for (int i = 0; i < px.length; i++) {
            String at = (i % n - n / 2) + "," + (i / n - n / 2);
            if (px[i]) {
                white++;
                assertEquals(0xFFFFFFFF, g.color.get(at), at);
            }
        }
        assertEquals(white, g.color.values().stream().filter(c -> c == 0xFFFFFFFF).count());
        // the outline: every pixel at most once and never under the crosshair itself
        for (Map.Entry<String, Integer> e : g.count.entrySet()) {
            assertEquals(1, e.getValue(), "drawn once: " + e.getKey());
        }
        assertEquals(0xB0000000, g.color.get("-5,0"), "outline left of the arm");
        assertEquals(0xFFFFFFFF, g.color.get("0,0"), "middle pixel on the centre");
    }

    @Test
    void hueOfColours() {
        assertEquals(0xFFFF0000, CrosshairEditor.hsv(0f));
        assertEquals(0xFF00FF00, CrosshairEditor.hsv(1 / 3f));
        assertEquals(0xFF0000FF, CrosshairEditor.hsv(2 / 3f));
        assertEquals(-1f, CrosshairEditor.hue(0xFFFFFFFF));
        assertEquals(-1f, CrosshairEditor.hue(0xFF000000));
        assertEquals(1 / 3f, CrosshairEditor.hue(0xFF00FF00), 0.01f);
        assertEquals(0.5f, CrosshairEditor.hue(CrosshairEditor.hsv(0.5f)), 0.01f);
    }
}
