package dev.lumora.core.perf;

import dev.lumora.api.render.HudGraphics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastShapesTest {
    /** Graphics that count how often each pixel is filled (overlaps would show as 2). */
    static final class Raster {
        final int[] cover = new int[200 * 200];
        int fills;
        final HudGraphics g;

        Raster() {
            InvocationHandler h = (proxy, method, args) -> {
                if (method.getName().equals("fill") && args != null && args.length == 5) {
                    fills++;
                    int x1 = (int) args[0];
                    int y1 = (int) args[1];
                    int x2 = (int) args[2];
                    int y2 = (int) args[3];
                    for (int y = y1; y < y2; y++) {
                        for (int x = x1; x < x2; x++) {
                            cover[(y + 20) * 200 + x + 20]++;
                        }
                    }
                    return null;
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                Class<?> r = method.getReturnType();
                return r == boolean.class ? false : r == int.class ? 0 : r == float.class ? 0f : null;
            };
            g = (HudGraphics) Proxy.newProxyInstance(HudGraphics.class.getClassLoader(), new Class<?>[]{HudGraphics.class}, h);
        }
    }

    @Test
    void samePixelsAsTheDefaultWithFewerRectangles() {
        int defaultFills = 0;
        int fastFills = 0;
        for (int w = 0; w <= 60; w += 3) {
            for (int h = 0; h <= 40; h += 2) {
                for (int r = 0; r <= 24; r++) {
                    Raster a = new Raster();
                    a.g.roundedRect(3, 5, w, h, r, 0x80FFFFFF);
                    Raster b = new Raster();
                    FastShapes.roundedRect(b.g, 3, 5, w, h, r, 0x80FFFFFF);
                    assertArrayEquals(a.cover, b.cover, "w=" + w + " h=" + h + " r=" + r);
                    for (int c : b.cover) {
                        assertTrue(c <= 1, "overlap at w=" + w + " h=" + h + " r=" + r);
                    }
                    defaultFills += a.fills;
                    fastFills += b.fills;
                }
            }
        }
        assertTrue(fastFills < defaultFills, fastFills + " vs " + defaultFills);
    }
}
