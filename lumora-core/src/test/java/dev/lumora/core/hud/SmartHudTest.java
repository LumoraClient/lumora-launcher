package dev.lumora.core.hud;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.perf.FastShapes;
import dev.lumora.core.perf.PerformanceEngine;
import dev.lumora.core.perf.TextWidths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Smart HUD and Frame Optimization for the widgets: same results as without, only computed less often. */
class SmartHudTest {
    @AfterEach
    void reset() {
        PerformanceEngine.smartHud = false;
        PerformanceEngine.frame = false;
        TextWidths.clear();
    }

    /** Records every fill call (position, size and colour, in order). */
    private static List<String> fills(java.util.function.Consumer<HudGraphics> draw) {
        List<String> out = new ArrayList<>();
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getName().equals("fill") && args != null && args.length == 5) {
                out.add(args[0] + "," + args[1] + "," + args[2] + "," + args[3] + "," + args[4]);
                return null;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            Class<?> r = method.getReturnType();
            return r == boolean.class ? false : r == int.class ? 0 : r == float.class ? 0f : null;
        };
        draw.accept((HudGraphics) Proxy.newProxyInstance(HudGraphics.class.getClassLoader(), new Class<?>[]{HudGraphics.class}, h));
        return out;
    }

    @Test
    void cachedOutlineDrawsTheSameRectanglesInTheSameOrder() {
        for (int w = 0; w <= 40; w += 3) {
            for (int h = 0; h <= 30; h += 2) {
                for (int r = 0; r <= 12; r++) {
                    int ww = w;
                    int hh = h;
                    int rr = r;
                    PerformanceEngine.frame = false;
                    List<String> plain = fills(g -> WidgetDesign.outline(g, 3, 5, ww, hh, rr, 0x40FFFFFF));
                    List<String> fast = fills(g -> FastShapes.roundedOutline(g, 3, 5, ww, hh, rr, 0x40FFFFFF));
                    assertEquals(plain, fast, "w=" + w + " h=" + h + " r=" + r);
                    PerformanceEngine.frame = true;
                    assertEquals(plain, fills(g -> WidgetDesign.outline(g, 3, 5, ww, hh, rr, 0x40FFFFFF)));
                }
            }
        }
    }

    @Test
    void keptLabelsAreTheLabelsOfTheLook() {
        String[] formats = {WidgetDesign.SPACE, WidgetDesign.COLON, WidgetDesign.BRACKETS, WidgetDesign.VALUE_FIRST, WidgetDesign.VALUE_ONLY};
        String[] labels = {"", "FPS", "xyz:", "Ping", "Ä ö"};
        for (String format : formats) {
            for (boolean upper : new boolean[]{false, true}) {
                WidgetDesign.Look look = new WidgetDesign.Look(true, 0x80000000, 2, true, 0x40FFFFFF, true, 0, 0, 0xFFFFFFFF,
                        true, format, upper, 1f);
                for (String label : labels) {
                    PerformanceEngine.smartHud = false;
                    String plain = WidgetDesign.drawnLabel(look, label);
                    PerformanceEngine.smartHud = true;
                    assertEquals(plain, WidgetDesign.drawnLabel(look, label), format + " " + upper + " " + label);
                    assertEquals(plain, WidgetDesign.drawnLabel(look, label));
                }
            }
        }
        // an equal look (read again every 50 ms) keeps the labels; a changed one builds them again
        PerformanceEngine.smartHud = true;
        WidgetDesign.Look a = new WidgetDesign.Look(true, 0, 0, false, 0, true, 0, 0, 0, true, WidgetDesign.COLON, false, 1f);
        WidgetDesign.Look b = new WidgetDesign.Look(true, 0, 0, false, 0, true, 0, 0, 0, true, WidgetDesign.COLON, false, 1f);
        WidgetDesign.Look c = new WidgetDesign.Look(true, 0, 0, false, 0, true, 0, 0, 0, true, WidgetDesign.BRACKETS, false, 1f);
        String first = WidgetDesign.drawnLabel(a, "FPS");
        assertSame(first, WidgetDesign.drawnLabel(b, "FPS"));
        assertEquals("[FPS]", WidgetDesign.drawnLabel(c, "FPS"));
    }

    /** Graphics with fill runs that log every call; a run may only contain rectangles. */
    private static List<String> runLog(java.util.function.Consumer<HudGraphics> draw) {
        List<String> log = new ArrayList<>();
        int[] depth = {0};
        InvocationHandler h = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "beginFills" -> {
                    depth[0]++;
                    log.add("begin");
                    return null;
                }
                case "endFills" -> {
                    depth[0]--;
                    log.add("end");
                    return null;
                }
                case "fill" -> {
                    if (args != null && args.length == 5) {
                        log.add("fill");
                        return null;
                    }
                }
                default -> {
                }
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            log.add(depth[0] > 0 ? "OTHER-IN-RUN:" + name : name);
            Class<?> r = method.getReturnType();
            return r == boolean.class ? false : r == int.class ? 0 : r == float.class ? 0f : null;
        };
        draw.accept((HudGraphics) Proxy.newProxyInstance(HudGraphics.class.getClassLoader(),
                new Class<?>[]{HudGraphics.class, dev.lumora.core.perf.FillBatch.class}, h));
        assertEquals(0, depth[0], "every run is closed");
        return log;
    }

    @Test
    void fillRunsContainOnlyRectanglesAndAreClosed() {
        PerformanceEngine.frame = true;
        WidgetDesign.use(() -> new WidgetDesign.Look(true, 0x80000000, 4, true, 0x40FFFFFF, true, 0, 0, 0xFFFFFFFF,
                true, WidgetDesign.COLON, true, 1f));
        try {
            List<String> log = runLog(g -> {
                WidgetDesign.panel(g, 80, 30);
                FastShapes.roundedRect(g, 0, 0, 40, 20, 5, 0x80FFFFFF);
                WidgetDesign.box(g, 0, 0, 40, 20, 3, 0x80000000);
            });
            assertEquals("begin", log.get(0));
            for (String s : log) {
                assertEquals(false, s.startsWith("OTHER-IN-RUN"), s);
            }
            assertEquals(true, log.stream().filter("fill"::equals).count() > 10);
            // without Frame Optimization no runs at all
            PerformanceEngine.frame = false;
            assertEquals(false, runLog(g -> WidgetDesign.panel(g, 80, 30)).contains("begin"));
        } finally {
            WidgetDesign.use(null);
        }
    }

    @Test
    void widthsAreDroppedWhenTheFontChanges() {
        TextWidths.clear();
        TextWidths.validate(0);
        TextWidths.put("FPS 60", 33);
        assertEquals(33, TextWidths.get("FPS 60"));
        TextWidths.validate(0);
        assertEquals(33, TextWidths.get("FPS 60"));
        int epoch = TextWidths.epoch();
        TextWidths.validate(1); // "Force Unicode Font" switched
        assertEquals(-1, TextWidths.get("FPS 60"));
        assertNotEquals(epoch, TextWidths.epoch());
        TextWidths.put("FPS 60", 40);
        epoch = TextWidths.epoch();
        TextWidths.clear(); // resource reload
        assertEquals(-1, TextWidths.get("FPS 60"));
        assertNotEquals(epoch, TextWidths.epoch());
    }
}
